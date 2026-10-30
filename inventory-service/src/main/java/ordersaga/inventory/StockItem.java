package ordersaga.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @Column(length = 40)
    private String sku;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int available;

    @Version
    private long version;

    protected StockItem() {
    }

    boolean canTake(int quantity) {
        return available >= quantity;
    }

    void take(int quantity) {
        if (!canTake(quantity)) {
            throw new IllegalStateException("Estoque insuficiente de " + sku);
        }
        available -= quantity;
    }

    void giveBack(int quantity) {
        available += quantity;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public int getAvailable() { return available; }
}
