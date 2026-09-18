package com.travel_system.backend_app.config;

import com.travel_system.backend_app.infrastructure.TenantContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(runnable -> {
            UUID currentTenant = TenantContext.getCurrentTenant();

            return () -> {
                try {
                    TenantContext.setCurrentTenant(currentTenant);
                    runnable.run();
                } finally {
                    TenantContext.removeCurrentTenant();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}

/*
* GUIDE
* propaga o tenantContext atual (customerId) para threads async antes de começar a execução dela
* */
