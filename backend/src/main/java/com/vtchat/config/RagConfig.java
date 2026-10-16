package com.vtchat.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@RequiredArgsConstructor
public class RagConfig {

    private final RagProperties props;

    @Bean(name = "ragExecutor")
    public Executor ragExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        int threads = Math.max(1, props.getProcessingThreads());
        ex.setCorePoolSize(threads);
        ex.setMaxPoolSize(threads);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("rag-ingest-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.initialize();
        return ex;
    }
}
