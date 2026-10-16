package com.vtchat.config;

import java.time.Duration;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;

import com.vtchat.redis.RealtimeEventSubscriber;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(RedisProperties props) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(props.getHost(), props.getPort());
        server.setDatabase(props.getDatabase());
        if (StringUtils.hasText(props.getPassword())) {
            server.setPassword(props.getPassword());
        }
        Duration command = props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(2);
        Duration connect = props.getConnectTimeout() != null ? props.getConnectTimeout() : Duration.ofSeconds(2);

        ClientOptions options = ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(connect).build())
                .timeoutOptions(TimeoutOptions.enabled(command))
                .build();

        GenericObjectPoolConfig<Object> pool = new GenericObjectPoolConfig<>();
        RedisProperties.Pool configured = props.getLettuce() != null ? props.getLettuce().getPool() : null;
        pool.setMaxTotal(configured != null ? configured.getMaxActive() : 8);
        pool.setMaxIdle(configured != null ? configured.getMaxIdle() : 8);
        pool.setMinIdle(configured != null ? configured.getMinIdle() : 0);
        pool.setMaxWait(configured != null && configured.getMaxWait() != null ? configured.getMaxWait()
                : Duration.ofSeconds(2));

        LettuceClientConfiguration client = LettucePoolingClientConfiguration.builder()
                .poolConfig(pool)
                .commandTimeout(command)
                .clientOptions(options)
                .build();
        log.info("Redis configured for {}:{} (database {})", props.getHost(), props.getPort(), props.getDatabase());
        return new LettuceConnectionFactory(server, client);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public RedisMessageListenerContainer redisListenerContainer(
            RedisConnectionFactory factory,
            RealtimeEventSubscriber subscriber,
            ScaleProperties scale) {

        if (!scale.getRedis().isEnabled()) {
            return null;
        }

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();

        container.setConnectionFactory(factory);
        container.setRecoveryInterval(5_000L);
        container.setErrorHandler(
                e -> log.warn("Redis Pub/Sub error: {}",
                        e.getClass().getSimpleName()));

        ScaleProperties.Channels channels = scale.getRedis().getChannels();

        for (String channel : new String[] {
                channels.getMessages(),
                channels.getNotifications(),
                channels.getCalls(),
                channels.getReadState(),
                channels.getPresence()
        }) {
            container.addMessageListener(
                    subscriber,
                    new ChannelTopic(channel));
        }

        return container;
    }

}
