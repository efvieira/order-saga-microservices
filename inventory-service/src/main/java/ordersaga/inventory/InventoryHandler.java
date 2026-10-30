package ordersaga.inventory;

import ordersaga.common.inbox.Inbox;
import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.SagaMessage;
import ordersaga.common.messages.SagaMessage.InventoryRejected;
import ordersaga.common.messages.SagaMessage.InventoryReserved;
import ordersaga.common.messages.SagaMessage.ReleaseInventory;
import ordersaga.common.messages.SagaMessage.ReserveInventory;
import ordersaga.common.messages.Topics;
import ordersaga.common.outbox.Outbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class InventoryHandler {

    private static final Logger log = LoggerFactory.getLogger(InventoryHandler.class);

    public interface StockRepository extends JpaRepository<StockItem, String> {
        List<StockItem> findAllByOrderBySku();
    }

    public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    }

    private final StockRepository stock;
    private final ReservationRepository reservations;
    private final Inbox inbox;
    private final Outbox outbox;
    private final SagaCodec codec;

    public InventoryHandler(StockRepository stock, ReservationRepository reservations, Inbox inbox, Outbox outbox, SagaCodec codec) {
        this.stock = stock;
        this.reservations = reservations;
        this.inbox = inbox;
        this.outbox = outbox;
        this.codec = codec;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS, groupId = "inventory-service")
    @Transactional
    public void onCommand(String payload) {
        var command = codec.decode(payload);
        if (!inbox.firstTime(command.messageId())) {
            return;
        }
        switch (command) {
            case ReserveInventory r -> reserve(r);
            case ReleaseInventory r -> release(r.orderId());
            default -> log.warn("Comando inesperado no estoque: {}", command.getClass().getSimpleName());
        }
    }

    /** Tudo ou nada: se um item faltar, nenhum é reservado. */
    private void reserve(ReserveInventory command) {
        var items = stock.findAllById(command.items().stream().map(SagaMessage.Item::sku).toList()).stream()
                .collect(Collectors.toMap(StockItem::getSku, Function.identity()));
        var missing = command.items().stream()
                .filter(i -> !items.containsKey(i.sku()) || !items.get(i.sku()).canTake(i.quantity()))
                .map(SagaMessage.Item::sku)
                .toList();
        if (!missing.isEmpty()) {
            outbox.send(new InventoryRejected(SagaMessage.newId(), command.orderId(), "Sem estoque: " + String.join(", ", missing)));
            return;
        }
        command.items().forEach(i -> items.get(i.sku()).take(i.quantity()));
        reservations.save(new Reservation(command.orderId(),
                command.items().stream().map(i -> new Reservation.Line(i.sku(), i.quantity())).toList()));
        outbox.send(new InventoryReserved(SagaMessage.newId(), command.orderId()));
    }

    /** Compensação idempotente: devolver duas vezes não duplica o estoque. */
    private void release(UUID orderId) {
        reservations.findById(orderId).filter(r -> !r.isReleased()).ifPresent(reservation -> {
            reservation.getLines().forEach(l -> stock.findById(l.getSku()).ifPresent(s -> s.giveBack(l.getQuantity())));
            reservation.release();
            log.info("Estoque do pedido {} liberado", orderId);
        });
    }

    @RestController
    static class StockController {

        record StockView(String sku, String name, int available) {
        }

        private final StockRepository stock;

        StockController(StockRepository stock) {
            this.stock = stock;
        }

        @GetMapping("/stock")
        List<StockView> list() {
            return stock.findAllByOrderBySku().stream().map(s -> new StockView(s.getSku(), s.getName(), s.getAvailable())).toList();
        }
    }
}
