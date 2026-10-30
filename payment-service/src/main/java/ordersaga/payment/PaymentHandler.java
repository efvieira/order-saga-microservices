package ordersaga.payment;

import ordersaga.common.inbox.Inbox;
import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.SagaMessage;
import ordersaga.common.messages.SagaMessage.PaymentApproved;
import ordersaga.common.messages.SagaMessage.PaymentDeclined;
import ordersaga.common.messages.SagaMessage.ProcessPayment;
import ordersaga.common.messages.SagaMessage.RefundPayment;
import ordersaga.common.messages.Topics;
import ordersaga.common.outbox.Outbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Autorizador simulado. Regras para a demo: recusa valores acima do limite configurado e
 * clientes cujo id começa com "bloqueado".
 */
@Component
public class PaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentHandler.class);

    public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    }

    private final PaymentRepository payments;
    private final Inbox inbox;
    private final Outbox outbox;
    private final SagaCodec codec;
    private final BigDecimal limit;

    public PaymentHandler(PaymentRepository payments, Inbox inbox, Outbox outbox, SagaCodec codec,
                          @Value("${payment.limit:5000.00}") BigDecimal limit) {
        this.payments = payments;
        this.inbox = inbox;
        this.outbox = outbox;
        this.codec = codec;
        this.limit = limit;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "payment-service")
    @Transactional
    public void onCommand(String payload) {
        var command = codec.decode(payload);
        if (!inbox.firstTime(command.messageId())) {
            return;
        }
        switch (command) {
            case ProcessPayment p -> charge(p);
            case RefundPayment r -> payments.findById(r.orderId())
                    .filter(Payment::refund)
                    .ifPresent(p -> log.info("Pagamento do pedido {} estornado", r.orderId()));
            default -> log.warn("Comando inesperado no pagamento: {}", command.getClass().getSimpleName());
        }
    }

    private void charge(ProcessPayment command) {
        if (payments.existsById(command.orderId())) {
            return; // comando reenviado com outro messageId: o pedido já foi cobrado
        }
        String reason = null;
        if (command.amount().compareTo(limit) > 0) {
            reason = "Valor acima do limite de " + limit;
        } else if (command.customerId().startsWith("bloqueado")) {
            reason = "Cliente com restrição";
        }
        if (reason != null) {
            payments.save(Payment.declined(command.orderId(), command.customerId(), command.amount(), reason));
            outbox.send(new PaymentDeclined(SagaMessage.newId(), command.orderId(), reason));
        } else {
            var tx = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            payments.save(Payment.approved(command.orderId(), command.customerId(), command.amount(), tx));
            outbox.send(new PaymentApproved(SagaMessage.newId(), command.orderId(), tx));
        }
    }

    @RestController
    static class PaymentController {

        record PaymentView(UUID orderId, BigDecimal amount, Payment.Status status, String transactionId, String reason) {
        }

        private final PaymentRepository payments;

        PaymentController(PaymentRepository payments) {
            this.payments = payments;
        }

        @GetMapping("/payments/{orderId}")
        ResponseEntity<PaymentView> get(@PathVariable UUID orderId) {
            return payments.findById(orderId)
                    .map(p -> new PaymentView(p.getOrderId(), p.getAmount(), p.getStatus(), p.getTransactionId(), p.getReason()))
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
    }
}
