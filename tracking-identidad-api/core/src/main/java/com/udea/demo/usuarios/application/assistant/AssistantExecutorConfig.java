package com.udea.demo.usuarios.application.assistant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(name="app.assistant.enabled",havingValue="true")
public class AssistantExecutorConfig {
    @Bean("assistantExecutor")
    public ThreadPoolTaskExecutor assistantExecutor() {
        var executor=new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);executor.setMaxPoolSize(8);executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("assistant-");executor.setWaitForTasksToCompleteOnShutdown(false);
        return executor;
    }
}
