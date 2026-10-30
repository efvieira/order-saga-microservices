package ordersaga.order;

import ordersaga.common.messages.SagaMessage;
import ordersaga.common.messages.SagaMessage.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");

    private Order order() {
        return new Order("cliente-1", "01310100", List.of(new OrderLine("SKU-1", 2, new BigDecimal("50.00"))), now);
    }

    @Test
    void happyPathReservesPaysShipsAndCompletes() {
        var order = order();
        assertThat(order.start()).isInstanceOf(ReserveInventory.class);

        var afterInventory = order.handle(new InventoryReserved(SagaMessage.newId(), order.getId()), now);
        assertThat(afterInventory).singleElement().isInstanceOfSatisfying(ProcessPayment.class,
                p -> assertThat(p.amount()).isEqualByComparingTo("100.00"));

        var afterPayment = order.handle(new PaymentApproved(SagaMessage.newId(), order.getId(), "tx-1"), now);
        assertThat(afterPayment).singleElement().isInstanceOf(ScheduleShipping.class);

        assertThat(order.handle(new ShippingScheduled(SagaMessage.newId(), order.getId(), "BR1"), now)).isEmpty();
        assertThat(order.getStatus()).isEqualTo(Order.Status.COMPLETED);
        assertThat(order.getTrackingCode()).isEqualTo("BR1");
        assertThat(order.getHistory()).extracting(StatusChange::getStatus).containsExactly(
                Order.Status.AWAITING_INVENTORY, Order.Status.AWAITING_PAYMENT,
                Order.Status.AWAITING_SHIPPING, Order.Status.COMPLETED);
    }

    @Test
    void outOfStockCancelsWithoutCompensation() {
        var order = order();
        assertThat(order.handle(new InventoryRejected(SagaMessage.newId(), order.getId(), "sem estoque"), now)).isEmpty();
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
        assertThat(order.getFailureReason()).isEqualTo("sem estoque");
    }

    @Test
    void declinedPaymentReleasesInventory() {
        var order = order();
        order.handle(new InventoryReserved(SagaMessage.newId(), order.getId()), now);
        assertThat(order.handle(new PaymentDeclined(SagaMessage.newId(), order.getId(), "limite"), now))
                .singleElement().isInstanceOf(ReleaseInventory.class);
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
    }

    @Test
    void shippingFailureRefundsAndReleasesInReverseOrder() {
        var order = order();
        order.handle(new InventoryReserved(SagaMessage.newId(), order.getId()), now);
        order.handle(new PaymentApproved(SagaMessage.newId(), order.getId(), "tx"), now);

        var compensations = order.handle(new ShippingFailed(SagaMessage.newId(), order.getId(), "fora da área"), now);
        assertThat(compensations).hasExactlyElementsOfTypes(RefundPayment.class, ReleaseInventory.class);
        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
    }

    @Test
    void ignoresRepliesThatDoNotMatchTheCurrentStep() {
        var order = order();
        assertThat(order.handle(new PaymentApproved(SagaMessage.newId(), order.getId(), "tx"), now)).isEmpty();
        assertThat(order.getStatus()).isEqualTo(Order.Status.AWAITING_INVENTORY);
        assertThatThrownBy(() -> new Order("c", "01310100", List.of(), now)).isInstanceOf(IllegalArgumentException.class);
    }
}
