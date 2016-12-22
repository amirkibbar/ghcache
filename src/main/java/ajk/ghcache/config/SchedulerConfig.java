package ajk.ghcache.config;

import ajk.ghcache.services.CacheService;
import ajk.ghcache.services.ViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
public class SchedulerConfig implements SchedulingConfigurer {
    @Autowired
    private GHCacheProperties props;

    @Autowired
    private ViewService viewService;

    @Autowired
    private CacheService cacheService;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // register the view refresh task
        taskRegistrar.addFixedRateTask(() -> viewService.cacheViews(), props.getViewRefreshMinutes() * 60 * 1000);

        // register the cached URIs refresh task
        taskRegistrar.addFixedRateTask(() -> cacheService.rebuild(), props.getCachedUrisRefreshMinutes() * 60 * 1000);
    }
}
