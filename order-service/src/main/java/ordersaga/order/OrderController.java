package ordersaga.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    public record LineRequest(@NotBlank String sku, @Min(1) int quantity, @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {
    }

    public record PlaceOrder(@NotBlank String customerId,
                             @Pattern(regexp = "\\d{8}", message = "CEP com 8 dígitos") String zipCode,
                             @NotEmpty List<@Valid LineRequest> lines) {
    }

    public record OrderView(UUID id, String customerId, Order.Status status, BigDecimal total, String failureReason,
                            String transactionId, String trackingCode, List<OrderLine> lines, List<StatusChange> history) {
        static OrderView from(Order o) {
            return new OrderView(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotal(), o.getFailureReason(),
                    o.getTransactionId(), o.getTrackingCode(), o.getLines(), o.getHistory());
        }
    }

    private final OrderSaga saga;
    private final OrderSaga.OrderRepository orders;

    public OrderController(OrderSaga saga, OrderSaga.OrderRepository orders) {
        this.saga = saga;
        this.orders = orders;
    }

    /** 202: o pedido foi aceito e a saga segue de forma assíncrona; consulte o status pelo id. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderView place(@Valid @RequestBody PlaceOrder request) {
        var lines = request.lines().stream().map(l -> new OrderLine(l.sku(), l.quantity(), l.unitPrice())).toList();
        return OrderView.from(saga.place(request.customerId(), request.zipCode(), lines));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderView> get(@PathVariable UUID id) {
        return orders.findById(id).map(OrderView::from).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
