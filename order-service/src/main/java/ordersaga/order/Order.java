package ordersaga.order;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import ordersaga.common.messages.SagaMessage;
import ordersaga.common.messages.SagaMessage.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Pedido e, ao mesmo tempo, o estado da saga orquestrada. Cada resposta de um participante
 * avança a máquina de estados e devolve os próximos comandos. Falhas disparam compensações
 * na ordem inversa: estorno do pagamento e liberação do estoque.
 *
 * <pre>
 * AWAITING_INVENTORY --reservado--> AWAITING_PAYMENT --aprovado--> AWAITING_SHIPPING --agendado--> COMPLETED
 *        |                                |                              |
 *     rejeitado                       recusado                         falhou
 *        v                                v                              v
 *    CANCELLED          CANCELLED (+ libera estoque)   CANCELLED (+ estorna e libera estoque)
 * </pre>
 */
@Entity(name = "PurchaseOrder")
@Table(name = "orders")
public class Order {

    public enum Status { AWAITING_INVENTORY, AWAITING_PAYMENT, AWAITING_SHIPPING, COMPLETED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, length = 60)
    private String customerId;

    @Column(name = "zip_code", nullable = false, length = 8)
    private String zipCode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "position")
    private List<OrderLine> lines = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "transaction_id", length = 60)
    private String transactionId;

    @Column(name = "tracking_code", length = 60)
    private String trackingCode;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_history", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "position")
    private List<StatusChange> history = new ArrayList<>();

    @Version
    private long version;

    protected Order() {
    }

    Order(String customerId, String zipCode, List<OrderLine> lines, Instant now) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Pedido sem itens");
        }
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.zipCode = zipCode;
        this.lines.addAll(lines);
        this.total = lines.stream().map(OrderLine::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        move(Status.AWAITING_INVENTORY, "pedido criado", now);
    }

    /** Primeiro passo da saga. */
    SagaMessage start() {
        return new ReserveInventory(SagaMessage.newId(), id,
                lines.stream().map(l -> new Item(l.getSku(), l.getQuantity())).toList());
    }

    /**
     * Aplica uma resposta e devolve os comandos seguintes. Respostas fora de ordem ou que
     * não combinam com o estado atual são ignoradas (lista vazia).
     */
    List<SagaMessage> handle(SagaMessage reply, Instant now) {
        return switch (reply) {
            case InventoryReserved _ when status == Status.AWAITING_INVENTORY -> {
                move(Status.AWAITING_PAYMENT, "estoque reservado", now);
                yield List.of(new ProcessPayment(SagaMessage.newId(), id, customerId, total));
            }
            case InventoryRejected r when status == Status.AWAITING_INVENTORY -> {
                cancel(r.reason(), now);
                yield List.of();
            }
            case PaymentApproved p when status == Status.AWAITING_PAYMENT -> {
                transactionId = p.transactionId();
                move(Status.AWAITING_SHIPPING, "pagamento aprovado", now);
                yield List.of(new ScheduleShipping(SagaMessage.newId(), id, zipCode));
            }
            case PaymentDeclined d when status == Status.AWAITING_PAYMENT -> {
                cancel(d.reason(), now);
                yield List.of(new ReleaseInventory(SagaMessage.newId(), id));
            }
            case ShippingScheduled s when status == Status.AWAITING_SHIPPING -> {
                trackingCode = s.trackingCode();
                move(Status.COMPLETED, "entrega agendada", now);
                yield List.of();
            }
            case ShippingFailed f when status == Status.AWAITING_SHIPPING -> {
                cancel(f.reason(), now);
                yield List.of(new RefundPayment(SagaMessage.newId(), id), new ReleaseInventory(SagaMessage.newId(), id));
            }
            default -> List.of();
        };
    }

    private void cancel(String reason, Instant now) {
        failureReason = reason;
        move(Status.CANCELLED, reason, now);
    }

    private void move(Status next, String detail, Instant now) {
        status = next;
        history.add(new StatusChange(next, detail, now));
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getZipCode() { return zipCode; }
    public List<OrderLine> getLines() { return Collections.unmodifiableList(lines); }
    public BigDecimal getTotal() { return total; }
    public Status getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getTransactionId() { return transactionId; }
    public String getTrackingCode() { return trackingCode; }
    public List<StatusChange> getHistory() { return Collections.unmodifiableList(history); }
}
