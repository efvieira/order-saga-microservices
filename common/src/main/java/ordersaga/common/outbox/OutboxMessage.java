package ordersaga.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxMessage {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    protected OutboxMessage() {
    }

    OutboxMessage(UUID id, String topic, String key, String payload, Instant createdAt) {
        this.id = id;
        this.topic = topic;
        this.key = key;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    void published(Instant at) {
        publishedAt = at;
    }

    void failed() {
        attempts++;
    }

    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getKey() { return key; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }
}
