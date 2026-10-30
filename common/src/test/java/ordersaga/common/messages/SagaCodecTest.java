package ordersaga.common.messages;

import ordersaga.common.messages.SagaMessage.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SagaCodecTest {

    private static final UUID ORDER = UUID.randomUUID();
    private final SagaCodec codec = new SagaCodec(JsonMapper.builder().build());

    static Stream<SagaMessage> messages() {
        return Stream.of(
                new ReserveInventory(SagaMessage.newId(), ORDER, List.of(new Item("SKU-1", 2))),
                new ReleaseInventory(SagaMessage.newId(), ORDER),
                new InventoryReserved(SagaMessage.newId(), ORDER),
                new InventoryRejected(SagaMessage.newId(), ORDER, "sem estoque"),
                new ProcessPayment(SagaMessage.newId(), ORDER, "c1", new BigDecimal("10.50")),
                new RefundPayment(SagaMessage.newId(), ORDER),
                new PaymentApproved(SagaMessage.newId(), ORDER, "tx"),
                new PaymentDeclined(SagaMessage.newId(), ORDER, "limite"),
                new ScheduleShipping(SagaMessage.newId(), ORDER, "01310100"),
                new ShippingScheduled(SagaMessage.newId(), ORDER, "BR123"),
                new ShippingFailed(SagaMessage.newId(), ORDER, "fora da área"));
    }

    @ParameterizedTest
    @MethodSource("messages")
    void roundTripKeepsTypeAndData(SagaMessage message) {
        var json = codec.encode(message);
        assertThat(json).contains("\"type\":\"" + message.getClass().getSimpleName() + "\"");
        assertThat(codec.decode(json)).isEqualTo(message);
    }

    @ParameterizedTest
    @MethodSource("messages")
    void commandsGoToParticipantsAndRepliesToOrchestrator(SagaMessage message) {
        var name = message.getClass().getSimpleName();
        var expected = switch (name) {
            case "ReserveInventory", "ReleaseInventory" -> Topics.INVENTORY_COMMANDS;
            case "ProcessPayment", "RefundPayment" -> Topics.PAYMENT_COMMANDS;
            case "ScheduleShipping" -> Topics.SHIPPING_COMMANDS;
            default -> Topics.REPLIES;
        };
        assertThat(message.topic()).isEqualTo(expected);
    }
}
