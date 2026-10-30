package ordersaga.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

@Embeddable
public class StatusChange {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Order.Status status;

    @Column(length = 200)
    private String detail;

    @Column(name = "changed_at", nullable = false)
    private Instant at;

    protected StatusChange() {
    }

    StatusChange(Order.Status status, String detail, Instant at) {
        this.status = status;
        this.detail = detail;
        this.at = at;
    }

    public Order.Status getStatus() { return status; }
    public String getDetail() { return detail; }
    public Instant getAt() { return at; }
}
