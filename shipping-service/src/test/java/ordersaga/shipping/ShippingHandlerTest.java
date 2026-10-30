package ordersaga.shipping;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippingHandlerTest {

    @Autowired ShippingHandler handler;
    @Autowired OutboxRepository outbox;
    @Autowired SagaCodec codec;
    @Autowired MockMvc mvc;

    @MockitoBean KafkaTemplate<String, String> kafka;

    private SagaMessage schedule(String zip, UUID order) {
        var command = new SagaMessage.ScheduleShipping(SagaMessage.newId(), order, zip);
        handler.onCommand(codec.encode(command));
        handler.onCommand(codec.encode(command));
        var replies = outbox.findAll().stream().map(m -> codec.decode(m.getPayload())).filter(m -> m.orderId().equals(order)).toList();
        assertThat(replies).hasSize(1);
        return replies.getFirst();
    }

    @Test
    void schedulesDeliveryWithTrackingCode() throws Exception {
        var order = UUID.randomUUID();
        assertThat(schedule("01310100", order)).isInstanceOfSatisfying(SagaMessage.ShippingScheduled.class,
                s -> assertThat(s.trackingCode()).matches("BR\\d{9}SG"));
        mvc.perform(get("/shipments/{id}", order)).andExpect(jsonPath("$.zipCode").value("01310100"));
    }

    @Test
    void failsForUnservedZipCodes() throws Exception {
        var order = UUID.randomUUID();
        assertThat(schedule("69000000", order)).isInstanceOf(SagaMessage.ShippingFailed.class);
        mvc.perform(get("/shipments/{id}", order)).andExpect(status().isNotFound());
        handler.onCommand(codec.encode(new SagaMessage.ReleaseInventory(SagaMessage.newId(), order)));
    }
}
