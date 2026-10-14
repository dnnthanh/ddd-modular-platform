package dev.demo.core.async;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableConfigurationProperties(ApplicationExecutorProperties.class)
public class ApplicationThreadPoolConfiguration {

  @Bean(name = "applicationTaskExecutor")
  ThreadPoolTaskExecutor applicationTaskExecutor(ApplicationExecutorProperties properties) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("app-worker-");
    executor.setCorePoolSize(properties.coreSize());
    executor.setMaxPoolSize(properties.maxSize());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
