package ordersaga.inventory;

import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.SagaMessage;
import ordersaga.common.messages.SagaMessage.Item;
import ordersaga.common.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryHandlerTest {

    @Autowired InventoryHandler handler;
    @Autowired InventoryHandler.StockRepository stock;
    @Autowired OutboxRepository outbox;
    @Autowired SagaCodec codec;
    @Autowired MockMvc mvc;

    @MockitoBean KafkaTemplate<String, String> kafka;

    private int available(String sku) {
        return stock.findById(sku).orElseThrow().getAvailable();
    }

    private List<SagaMessage> repliesFor(UUID orderId) {
        return outbox.findAll().stream().map(m -> codec.decode(m.getPayload())).filter(m -> m.orderId().equals(orderId)).toList();
    }

    @Test
    void reservesAllItemsAndReleasesOnCompensationOnlyOnce() {
        var order = UUID.randomUUID();
        var before = available("MOUSE-SEM-FIO");
        var reserve = new SagaMessage.ReserveInventory(SagaMessage.newId(), order, List.of(new Item("MOUSE-SEM-FIO", 3)));

        handler.onCommand(codec.encode(reserve));
        handler.onCommand(codec.encode(reserve)); // duplicata
        assertThat(available("MOUSE-SEM-FIO")).isEqualTo(before - 3);
        assertThat(repliesFor(order)).singleElement().isInstanceOf(SagaMessage.InventoryReserved.class);

        handler.onCommand(codec.encode(new SagaMessage.ReleaseInventory(SagaMessage.newId(), order)));
        handler.onCommand(codec.encode(new SagaMessage.ReleaseInventory(SagaMessage.newId(), order)));
        assertThat(available("MOUSE-SEM-FIO")).isEqualTo(before);
    }

    @Test
    void rejectsWholeOrderWhenAnyItemIsMissing() {
        var order = UUID.randomUUID();
        var before = available("NOTEBOOK-14");
        handler.onCommand(codec.encode(new SagaMessage.ReserveInventory(SagaMessage.newId(), order,
                List.of(new Item("NOTEBOOK-14", 1), new Item("TECLADO-MEC", 1), new Item("NAO-EXISTE", 1)))));

        assertThat(available("NOTEBOOK-14")).isEqualTo(before);
        assertThat(repliesFor(order)).singleElement().isInstanceOfSatisfying(SagaMessage.InventoryRejected.class,
                r -> assertThat(r.reason()).contains("TECLADO-MEC", "NAO-EXISTE"));
    }

    @Test
    void ignoresUnexpectedCommandsAndListsStock() throws Exception {
        handler.onCommand(codec.encode(new SagaMessage.RefundPayment(SagaMessage.newId(), UUID.randomUUID())));
        mvc.perform(get("/stock")).andExpect(jsonPath("$[?(@.sku == 'MONITOR-27')].available").value(5));
    }
}
