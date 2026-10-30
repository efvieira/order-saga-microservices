package ordersaga.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    public enum Status { APPROVED, DECLINED, REFUNDED }

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, length = 60)
    private String customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status;

    @Column(name = "transaction_id", length = 60)
    private String transactionId;

    @Column(length = 200)
    private String reason;

    protected Payment() {
    }

    static Payment approved(UUID orderId, String customerId, BigDecimal amount, String transactionId) {
        var p = new Payment(orderId, customerId, amount, Status.APPROVED);
        p.transactionId = transactionId;
        return p;
    }

    static Payment declined(UUID orderId, String customerId, BigDecimal amount, String reason) {
        var p = new Payment(orderId, customerId, amount, Status.DECLINED);
        p.reason = reason;
        return p;
    }

    private Payment(UUID orderId, String customerId, BigDecimal amount, Status status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
    }

    /** Estorno só faz sentido para pagamento aprovado; repetir não muda nada. */
    boolean refund() {
        if (status != Status.APPROVED) {
            return false;
        }
        status = Status.REFUNDED;
        return true;
    }

    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public String getReason() { return reason; }
}
