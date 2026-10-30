package ordersaga.payment;

import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.SagaMessage;
import ordersaga.common.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentHandlerTest {

    @Autowired PaymentHandler handler;
    @Autowired OutboxRepository outbox;
    @Autowired SagaCodec codec;
    @Autowired MockMvc mvc;

    @MockitoBean KafkaTemplate<String, String> kafka;

    private SagaMessage reply(UUID orderId) {
        return outbox.findAll().stream().map(m -> codec.decode(m.getPayload()))
                .filter(m -> m.orderId().equals(orderId)).findFirst().orElseThrow();
    }

    private UUID pay(String customer, String amount) {
        var order = UUID.randomUUID();
        handler.onCommand(codec.encode(new SagaMessage.ProcessPayment(SagaMessage.newId(), order, customer, new BigDecimal(amount))));
        return order;
    }

    @Test
    void approvesWithinLimitAndRefundsOnce() throws Exception {
        var order = pay("cliente-1", "120.00");
        assertThat(reply(order)).isInstanceOf(SagaMessage.PaymentApproved.class);

        handler.onCommand(codec.encode(new SagaMessage.RefundPayment(SagaMessage.newId(), order)));
        handler.onCommand(codec.encode(new SagaMessage.RefundPayment(SagaMessage.newId(), order)));
        mvc.perform(get("/payments/{id}", order)).andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void declinesAboveLimitOrBlockedCustomer() throws Exception {
        assertThat(reply(pay("cliente-2", "5000.01"))).isInstanceOfSatisfying(SagaMessage.PaymentDeclined.class,
                d -> assertThat(d.reason()).contains("limite"));
        var blocked = pay("bloqueado-9", "10.00");
        assertThat(reply(blocked)).isInstanceOf(SagaMessage.PaymentDeclined.class);
        mvc.perform(get("/payments/{id}", blocked)).andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void sameOrderIsNeverChargedTwice() throws Exception {
        var order = UUID.randomUUID();
        var command = new SagaMessage.ProcessPayment(SagaMessage.newId(), order, "c", BigDecimal.TEN);
        handler.onCommand(codec.encode(command));
        handler.onCommand(codec.encode(new SagaMessage.ProcessPayment(SagaMessage.newId(), order, "c", BigDecimal.TEN)));
        handler.onCommand(codec.encode(new SagaMessage.ShippingScheduled(SagaMessage.newId(), order, "x")));

        assertThat(outbox.findAll().stream().filter(m -> m.getKey().equals(order.toString()))).hasSize(1);
        mvc.perform(get("/payments/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
