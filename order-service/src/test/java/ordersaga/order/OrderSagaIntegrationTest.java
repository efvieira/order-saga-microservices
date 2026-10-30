package ordersaga.order;

import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.SagaMessage;
import ordersaga.common.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderSagaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired SagaCodec codec;
    @Autowired OrderSaga saga;
    @Autowired OutboxRepository outbox;

    @MockitoBean KafkaTemplate<String, String> kafka;

    private UUID placeOrder() throws Exception {
        var body = mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"customerId": "cliente-7", "zipCode": "01310100",
                         "lines": [{"sku": "NOTEBOOK-14", "quantity": 1, "unitPrice": 4500.00}]}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("AWAITING_INVENTORY"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asString());
    }

    private long outboxCount(Class<? extends SagaMessage> type, UUID orderId) {
        return outbox.findAll().stream()
                .map(m -> codec.decode(m.getPayload()))
                .filter(m -> type.isInstance(m) && m.orderId().equals(orderId))
                .count();
    }

    @Test
    void repliesAdvanceTheSagaThroughTheOutbox() throws Exception {
        var orderId = placeOrder();
        assertThat(outboxCount(SagaMessage.ReserveInventory.class, orderId)).isEqualTo(1);

        var reserved = new SagaMessage.InventoryReserved(SagaMessage.newId(), orderId);
        saga.onReply(codec.encode(reserved));
        saga.onReply(codec.encode(reserved)); // entrega duplicada pelo Kafka
        assertThat(outboxCount(SagaMessage.ProcessPayment.class, orderId)).isEqualTo(1);

        saga.onReply(codec.encode(new SagaMessage.PaymentApproved(SagaMessage.newId(), orderId, "tx-9")));
        saga.onReply(codec.encode(new SagaMessage.ShippingScheduled(SagaMessage.newId(), orderId, "BR777")));

        mvc.perform(get("/orders/{id}", orderId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.trackingCode").value("BR777"))
                .andExpect(jsonPath("$.history.length()").value(4));
    }

    @Test
    void declinedPaymentCompensatesInventory() throws Exception {
        var orderId = placeOrder();
        saga.onReply(codec.encode(new SagaMessage.InventoryReserved(SagaMessage.newId(), orderId)));
        saga.onReply(codec.encode(new SagaMessage.PaymentDeclined(SagaMessage.newId(), orderId, "limite excedido")));

        assertThat(outboxCount(SagaMessage.ReleaseInventory.class, orderId)).isEqualTo(1);
        mvc.perform(get("/orders/{id}", orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.failureReason").value("limite excedido"));
    }

    @Test
    void validatesInputAndUnknownOrders() throws Exception {
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": \"c\", \"zipCode\": \"123\", \"lines\": []}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/orders/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
        saga.onReply(codec.encode(new SagaMessage.InventoryReserved(SagaMessage.newId(), UUID.randomUUID())));
    }
}
