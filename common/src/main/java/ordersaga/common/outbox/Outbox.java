package ordersaga.common.outbox;

import ordersaga.common.messages.SagaCodec;
import ordersaga.common.messages.SagaMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

/**
 * Transactional outbox compartilhado: a mensagem é gravada junto com a mudança de estado do
 * serviço e publicada no Kafka por um relay. Assim nenhum serviço "esquece" de avisar a saga
 * quando o banco confirma, nem avisa algo que sofreu rollback.
 */
@Component
public class Outbox {

    private static final Logger log = LoggerFactory.getLogger(Outbox.class);

    private final OutboxRepository repository;
    private final SagaCodec codec;
    private final KafkaTemplate<String, String> kafka;
    private final Clock clock;

    public Outbox(OutboxRepository repository, SagaCodec codec, KafkaTemplate<String, String> kafka, Clock clock) {
        this.repository = repository;
        this.codec = codec;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void send(SagaMessage message) {
        repository.save(new OutboxMessage(message.messageId(), message.topic(), message.orderId().toString(),
                codec.encode(message), clock.instant()));
    }

    /** Publica em ordem de criação, usando o id do pedido como chave para manter a ordem por saga. */
    @Scheduled(fixedDelayString = "${outbox.poll-interval:PT0.5S}")
    @Transactional
    public int relay() {
        int published = 0;
        for (var message : repository.findByPublishedAtIsNullOrderByCreatedAt(Limit.of(100))) {
            try {
                kafka.send(message.getTopic(), message.getKey(), message.getPayload()).get(5, TimeUnit.SECONDS);
                message.published(clock.instant());
                published++;
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                message.failed();
                log.warn("Falha ao publicar {} em {}: {}", message.getId(), message.getTopic(), e.getMessage());
                break; // preserva a ordem: tenta de novo no próximo ciclo
            }
        }
        return published;
    }
}
