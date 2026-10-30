package ordersaga.common.messages;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Serializa as mensagens com o campo "type", para o consumidor saber qual record criar. */
@Component
public class SagaCodec {

    private final JsonMapper json;

    public SagaCodec(JsonMapper json) {
        this.json = json;
    }

    public String encode(SagaMessage message) {
        return json.writerFor(SagaMessage.class).writeValueAsString(message);
    }

    public SagaMessage decode(String payload) {
        return json.readValue(payload, SagaMessage.class);
    }
}
