package com.bbcc.kidly.boundedcontext.product.app.outbox;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("kidly.product.outbox")
public record ProductOutboxProperties(@Positive int batchSize, @NotNull Duration retention) {
}
