package ordersaga.shipping;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ordersaga.common.inbox.Inbox;
import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.SagaMessage;
import ordersaga.common.messages.SagaMessage.ScheduleShipping;
import ordersaga.common.messages.SagaMessage.ShippingFailed;
import ordersaga.common.messages.SagaMessage.ShippingScheduled;
import ordersaga.common.messages.Topics;
import ordersaga.common.outbox.Outbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Agenda a entrega, salvo quando o CEP está numa faixa não atendida pela transportadora. */
@Component
public class ShippingHandler {

    @Entity
    @Table(name = "shipments")
    public static class Shipment {
        @Id
        @Column(name = "order_id")
        private UUID orderId;

        @Column(name = "zip_code", nullable = false, length = 8)
        private String zipCode;

        @Column(name = "tracking_code", nullable = false, length = 20)
        private String trackingCode;

        @Column(name = "scheduled_at", nullable = false)
        private Instant scheduledAt;

        protected Shipment() {
        }

        Shipment(UUID orderId, String zipCode, String trackingCode, Instant scheduledAt) {
            this.orderId = orderId;
            this.zipCode = zipCode;
            this.trackingCode = trackingCode;
            this.scheduledAt = scheduledAt;
        }

        public UUID getOrderId() { return orderId; }
        public String getZipCode() { return zipCode; }
        public String getTrackingCode() { return trackingCode; }
    }

    public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    }

    private final ShipmentRepository shipments;
    private final Inbox inbox;
    private final Outbox outbox;
    private final SagaCodec codec;
    private final Clock clock;
    private final List<String> unservedPrefixes;

    public ShippingHandler(ShipmentRepository shipments, Inbox inbox, Outbox outbox, SagaCodec codec, Clock clock,
                           @Value("${shipping.unserved-zip-prefixes:69}") List<String> unservedPrefixes) {
        this.shipments = shipments;
        this.inbox = inbox;
        this.outbox = outbox;
        this.codec = codec;
        this.clock = clock;
        this.unservedPrefixes = unservedPrefixes;
    }

    @KafkaListener(topics = Topics.SHIPPING_COMMANDS, groupId = "shipping-service")
    @Transactional
    public void onCommand(String payload) {
        if (!(codec.decode(payload) instanceof ScheduleShipping command) || !inbox.firstTime(command.messageId())) {
            return;
        }
        if (unservedPrefixes.stream().anyMatch(command.zipCode()::startsWith)) {
            outbox.send(new ShippingFailed(SagaMessage.newId(), command.orderId(), "CEP " + command.zipCode() + " fora da área de entrega"));
            return;
        }
        var tracking = "BR%09dSG".formatted(ThreadLocalRandom.current().nextInt(1_000_000_000));
        shipments.save(new Shipment(command.orderId(), command.zipCode(), tracking, clock.instant()));
        outbox.send(new ShippingScheduled(SagaMessage.newId(), command.orderId(), tracking));
    }

    @RestController
    static class ShipmentController {

        record ShipmentView(UUID orderId, String zipCode, String trackingCode) {
        }

        private final ShipmentRepository shipments;

        ShipmentController(ShipmentRepository shipments) {
            this.shipments = shipments;
        }

        @GetMapping("/shipments/{orderId}")
        ResponseEntity<ShipmentView> get(@PathVariable UUID orderId) {
            return shipments.findById(orderId)
                    .map(s -> new ShipmentView(s.getOrderId(), s.getZipCode(), s.getTrackingCode()))
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
    }
}
