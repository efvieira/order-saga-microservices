package ordersaga.common.messages;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Contrato de mensagens da saga de pedidos. Comandos vão do orquestrador (order-service)
 * para cada participante; respostas voltam no tópico {@link Topics#REPLIES}.
 * Toda mensagem tem um messageId único, usado pelos consumidores para descartar duplicatas.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SagaMessage.ReserveInventory.class, name = "ReserveInventory"),
        @JsonSubTypes.Type(value = SagaMessage.ReleaseInventory.class, name = "ReleaseInventory"),
        @JsonSubTypes.Type(value = SagaMessage.InventoryReserved.class, name = "InventoryReserved"),
        @JsonSubTypes.Type(value = SagaMessage.InventoryRejected.class, name = "InventoryRejected"),
        @JsonSubTypes.Type(value = SagaMessage.ProcessPayment.class, name = "ProcessPayment"),
        @JsonSubTypes.Type(value = SagaMessage.RefundPayment.class, name = "RefundPayment"),
        @JsonSubTypes.Type(value = SagaMessage.PaymentApproved.class, name = "PaymentApproved"),
        @JsonSubTypes.Type(value = SagaMessage.PaymentDeclined.class, name = "PaymentDeclined"),
        @JsonSubTypes.Type(value = SagaMessage.ScheduleShipping.class, name = "ScheduleShipping"),
        @JsonSubTypes.Type(value = SagaMessage.ShippingScheduled.class, name = "ShippingScheduled"),
        @JsonSubTypes.Type(value = SagaMessage.ShippingFailed.class, name = "ShippingFailed")
})
public sealed interface SagaMessage {

    UUID messageId();

    UUID orderId();

    record Item(String sku, int quantity) {
    }

    // --- comandos ---------------------------------------------------------

    record ReserveInventory(UUID messageId, UUID orderId, List<Item> items) implements SagaMessage {
    }

    record ReleaseInventory(UUID messageId, UUID orderId) implements SagaMessage {
    }

    record ProcessPayment(UUID messageId, UUID orderId, String customerId, BigDecimal amount) implements SagaMessage {
    }

    record RefundPayment(UUID messageId, UUID orderId) implements SagaMessage {
    }

    record ScheduleShipping(UUID messageId, UUID orderId, String zipCode) implements SagaMessage {
    }

    // --- respostas --------------------------------------------------------

    record InventoryReserved(UUID messageId, UUID orderId) implements SagaMessage {
    }

    record InventoryRejected(UUID messageId, UUID orderId, String reason) implements SagaMessage {
    }

    record PaymentApproved(UUID messageId, UUID orderId, String transactionId) implements SagaMessage {
    }

    record PaymentDeclined(UUID messageId, UUID orderId, String reason) implements SagaMessage {
    }

    record ShippingScheduled(UUID messageId, UUID orderId, String trackingCode) implements SagaMessage {
    }

    record ShippingFailed(UUID messageId, UUID orderId, String reason) implements SagaMessage {
    }

    /** Tópico de destino de cada mensagem: comandos vão ao participante, respostas ao orquestrador. */
    default String topic() {
        return switch (this) {
            case ReserveInventory _, ReleaseInventory _ -> Topics.INVENTORY_COMMANDS;
            case ProcessPayment _, RefundPayment _ -> Topics.PAYMENT_COMMANDS;
            case ScheduleShipping _ -> Topics.SHIPPING_COMMANDS;
            case InventoryReserved _, InventoryRejected _, PaymentApproved _, PaymentDeclined _,
                 ShippingScheduled _, ShippingFailed _ -> Topics.REPLIES;
        };
    }

    static UUID newId() {
        return UUID.randomUUID();
    }
}
