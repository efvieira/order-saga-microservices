package ordersaga.order;

import ordersaga.common.inbox.Inbox;
import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.Topics;
import ordersaga.common.outbox.Outbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Orquestrador: cria pedidos e reage às respostas dos participantes. */
@Service
public class OrderSaga {

    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);

    public interface OrderRepository extends JpaRepository<Order, UUID> {
    }

    private final OrderRepository orders;
    private final Outbox outbox;
    private final Inbox inbox;
    private final SagaCodec codec;
    private final Clock clock;

    public OrderSaga(OrderRepository orders, Outbox outbox, Inbox inbox, SagaCodec codec, Clock clock) {
        this.orders = orders;
        this.outbox = outbox;
        this.inbox = inbox;
        this.codec = codec;
        this.clock = clock;
    }

    @Transactional
    public Order place(String customerId, String zipCode, List<OrderLine> lines) {
        var order = orders.save(new Order(customerId, zipCode, lines, clock.instant()));
        outbox.send(order.start());
        return order;
    }

    @KafkaListener(topics = Topics.REPLIES, groupId = "order-service")
    @Transactional
    public void onReply(String payload) {
        var reply = codec.decode(payload);
        if (!inbox.firstTime(reply.messageId())) {
            log.debug("Resposta duplicada ignorada: {}", reply.messageId());
            return;
        }
        orders.findById(reply.orderId()).ifPresentOrElse(order -> {
            var before = order.getStatus();
            order.handle(reply, clock.instant()).forEach(outbox::send);
            log.info("Pedido {}: {} -> {} ({})", order.getId(), before, order.getStatus(), reply.getClass().getSimpleName());
        }, () -> log.warn("Resposta para pedido inexistente {}", reply.orderId()));
    }
}
