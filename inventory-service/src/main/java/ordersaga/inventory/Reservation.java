package ordersaga.inventory;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Guarda o que foi reservado para cada pedido, para a compensação devolver exatamente isso. */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Embeddable
    public static class Line {
        @Column(nullable = false, length = 40)
        private String sku;

        @Column(nullable = false)
        private int quantity;

        protected Line() {
        }

        Line(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }

        public String getSku() { return sku; }
        public int getQuantity() { return quantity; }
    }

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<Line> lines = new ArrayList<>();

    @Column(nullable = false)
    private boolean released;

    protected Reservation() {
    }

    Reservation(UUID orderId, List<Line> lines) {
        this.orderId = orderId;
        this.lines.addAll(lines);
    }

    void release() {
        released = true;
    }

    public UUID getOrderId() { return orderId; }
    public List<Line> getLines() { return lines; }
    public boolean isReleased() { return released; }
}
