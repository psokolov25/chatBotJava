package ru.qsystems.telegrambot.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.inject.Singleton;

/**
 * Provides a default Micrometer registry when no external metrics backend is configured.
 *
 * <p>The application uses {@link MeterRegistry} in business services for timing and counters.
 * The plain {@code micrometer-core} dependency supplies the API, but it does not create a
 * Micronaut bean by itself. This fallback registry keeps metrics instrumentation optional and
 * prevents startup failures in local/dev deployments without Prometheus or another registry.</p>
 */
@Factory
public class MeterRegistryFactory {

    @Singleton
    @Requires(missingBeans = MeterRegistry.class)
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
