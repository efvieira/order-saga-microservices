package ordersaga.common;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Configuração comum a todos os serviços: escaneia o pacote "ordersaga" inteiro (o serviço e
 * o common), liga o agendador do outbox e fornece o Clock.
 */
@Configuration
@EnableScheduling
@EntityScan("ordersaga")
@EnableJpaRepositories(basePackages = "ordersaga", considerNestedRepositories = true)
public class ServiceDefaults {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
