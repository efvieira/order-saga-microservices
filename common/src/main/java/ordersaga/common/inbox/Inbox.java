package ordersaga.common.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Consumidor idempotente: o Kafka entrega at-least-once, então a mesma mensagem pode chegar
 * duas vezes. O id é gravado na mesma transação do processamento; se já existe, é duplicata.
 */
@Component
public class Inbox {

    public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, UUID> {
    }

    private final ProcessedMessageRepository repository;
    private final Clock clock;

    public Inbox(ProcessedMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** @return true se a mensagem é nova e deve ser processada */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(UUID messageId) {
        if (repository.existsById(messageId)) {
            return false;
        }
        repository.save(new ProcessedMessage(messageId, clock.instant()));
        return true;
    }
}
