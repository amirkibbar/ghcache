package ajk.ghcache;

import ajk.ghcache.config.GHCacheProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.boot.SpringApplication.run;

@SpringBootApplication
@ComponentScan(basePackages = "ajk")
@EnableConfigurationProperties(GHCacheProperties.class)
@EnableScheduling
@EnableCaching
public class GHCache {
    public static void main(String[] args) {
        run(GHCache.class, args);
    }
}
