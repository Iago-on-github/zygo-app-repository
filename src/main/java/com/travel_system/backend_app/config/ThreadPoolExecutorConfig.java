package com.travel_system.backend_app.config;

import com.travel_system.backend_app.service.AsyncNotificationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@EnableAsync
@Configuration
public class ThreadPoolExecutorConfig {

    @Primary
    // separar isso no system metrics e consertar os valores das props
    @Bean(name = "notificationTaskExecutor")
    public ThreadPoolTaskExecutor notificationTaskExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 15;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 5;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // nomeia a thread para identificação das threads principais do servidor
        executor.setThreadNamePrefix("FCM-Notification-");

        // params configuráveis
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        // define a política de rejeição - o que acontence quando a queue enche
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "vehicleGpsTaskExecutor")
    public ThreadPoolTaskExecutor vehicleGpsTaskExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 20;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 5;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // nomeia a thread para identificação das threads principais do servidor
        executor.setThreadNamePrefix("RBMQ-VehicleGps-");

        // params configuráveis
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        // define a política de rejeição - o que acontence quando a queue enche
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "locationProcessingTaskExecutor")
    public ThreadPoolTaskExecutor LocationProcessingExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 20;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 5;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // nomeia a thread para identificação das threads principais do servidor
        executor.setThreadNamePrefix("Location-Processing-");

        // params configuráveis
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        // define a política de rejeição - o que acontence quando a queue enche
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "studentAwayTaskExecutor")
    public ThreadPoolTaskExecutor studentAwayStateExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 30;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 5;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // nomeia a thread para identificação das threads principais do servidor
        executor.setThreadNamePrefix("Travel-Tracking-");

        // params configuráveis
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        // define a política de rejeição - o que acontence quando a queue enche
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "sendSensitiveEmailTaskExecutor")
    public ThreadPoolTaskExecutor sendSensitiveEmailExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 10;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 5;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setThreadNamePrefix("Send-Sensitive-Email");

        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        // rejeita e lança a exception
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "routeStopTaskExecutor")
    public ThreadPoolTaskExecutor routeStopLifecycleExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 10;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 5;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setThreadNamePrefix("RouteStop-Lifecycle-");

        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "routeStopApproachTaskExecutor")
    public ThreadPoolTaskExecutor routeStopApproachExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 50;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 4;
        int MAXIMUM_POOL_SIZE = 8;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setThreadNamePrefix("RouteStop-Approach-");

        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        // DiscardOldestPolicy descarta os pings mais antigos quando a queue está prestes a encher
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "staticNotificationTaskExecutor")
    public ThreadPoolTaskExecutor staticNotificationExecutor() {
        /*
        * notificações estáticas não tem observabilidade no SystemMetrics
        * */

        int MAXIMUM_QUEUE_CAPACITY = 10;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 5;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setThreadNamePrefix("FCM-Static-Notification-");

        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "routeRecalculationTaskExecutor")
    public ThreadPoolTaskExecutor routeRecalculationExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 20;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 6;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setThreadNamePrefix("Route-recalc-");

        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        return executor;
    }

    @Bean(name = "savedTravelLocationTaskExecutor")
    public ThreadPoolTaskExecutor travelLocationHistoryTaskExecutor() {
        int MAXIMUM_QUEUE_CAPACITY = 10;
        int KEEP_ALIVE_TIME_SECONDS = 30;
        int CORE_POOL_SIZE = 2;
        int MAXIMUM_POOL_SIZE = 6;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setThreadNamePrefix("Travel-Location-History-");

        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAXIMUM_POOL_SIZE);
        executor.setQueueCapacity(MAXIMUM_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(KEEP_ALIVE_TIME_SECONDS);

        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.initialize();

        return executor;
    }
}

/*
* GUIDE
* CorePoolSize: Quantas tarefas rodando simultâneamente em operação normal, fora de pico de uso.
* MaxPoolSize: Teto de paralelismo sob pico, acima do core. A diferença entre core e max é o quanto será tolerado de picos temporários em threads extra (memória, context switching) antes de preferir enfileirar.
* QueueCapacity: Quanto atraso é aceitável antes do dado processado virar obsoleto
* Keep-Alive-Time: só importa pras threads acima do core (as extras, criadas sob pico); é quanto tempo elas ficam ociosas antes de morrer.
*   Curto = libera recursos rápido depois do pico, mas paga o custo de recriar thread se o pico voltar logo.
*   Longo = mais estável em pico intermitente, mas segura recurso ocioso por mais tempo.
* Rejection policy: que fazer quando fila + pool máximo estourarem juntos
* */
