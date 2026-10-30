package ordersaga.common.outbox;

import ordersaga.common.inbox.Inbox;
import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.SagaMessage;
import ordersaga.common.messages.Topics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class OutboxTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final Outbox outbox = new Outbox(repository, new SagaCodec(JsonMapper.builder().build()), kafka, clock);

    @Test
    void storesMessageWithTopicAndOrderKey() {
        var order = UUID.randomUUID();
        outbox.send(new SagaMessage.InventoryReserved(UUID.randomUUID(), order));

        var captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo(Topics.REPLIES);
        assertThat(captor.getValue().getKey()).isEqualTo(order.toString());
    }

    @Test
    void relayStopsAtFirstFailureToPreserveOrder() {
        var first = new OutboxMessage(UUID.randomUUID(), "t", "k", "{}", clock.instant());
        var second = new OutboxMessage(UUID.randomUUID(), "t", "k", "{}", clock.instant());
        when(repository.findByPublishedAtIsNullOrderByCreatedAt(any(Limit.class))).thenReturn(List.of(first, second));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")));

        assertThat(outbox.relay()).isZero();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getAttempts()).isZero();
    }

    @Test
    void relayMarksPublishedMessages() {
        var message = new OutboxMessage(UUID.randomUUID(), "t", "k", "{}", clock.instant());
        when(repository.findByPublishedAtIsNullOrderByCreatedAt(any(Limit.class))).thenReturn(List.of(message));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(outbox.relay()).isEqualTo(1);
        assertThat(message.getPublishedAt()).isEqualTo(clock.instant());
    }

    @Test
    void inboxDetectsDuplicates() {
        var repo = mock(Inbox.ProcessedMessageRepository.class);
        var inbox = new Inbox(repo, clock);
        var id = UUID.randomUUID();
        when(repo.existsById(id)).thenReturn(false, true);

        assertThat(inbox.firstTime(id)).isTrue();
        assertThat(inbox.firstTime(id)).isFalse();
        verify(repo).save(any());
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
