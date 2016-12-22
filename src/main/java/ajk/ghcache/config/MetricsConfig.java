package ajk.ghcache.config;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Slf4jReporter;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

import static java.util.concurrent.TimeUnit.MINUTES;

@Configuration
public class MetricsConfig {
    @Bean
    public MetricRegistry metrics() {
        return new MetricRegistry();
    }

    @PostConstruct
    public void registerReporters() {
        Slf4jReporter slf4j = Slf4jReporter.forRegistry(metrics())
                .outputTo(LoggerFactory.getLogger("metrics"))
                .build();

        slf4j.start(1, MINUTES);
    }
}
