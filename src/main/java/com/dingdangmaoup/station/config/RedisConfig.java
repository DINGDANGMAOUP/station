package com.dingdangmaoup.station.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

  private final RedisProperties redisProperties;

  @Bean
  public LettuceConnectionFactory lettuceConnectionFactory() {
    LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder =
        LettuceClientConfiguration.builder()
            .commandTimeout(redisProperties.getTimeout());

    if (redisProperties.getMode() == RedisProperties.Mode.CLUSTER) {
      clientConfigBuilder.clientOptions(clusterClientOptions());
    } else {
      clientConfigBuilder.clientOptions(clientOptions());
    }

    LettuceClientConfiguration clientConfig = clientConfigBuilder.build();

    RedisConfiguration redisConfiguration = switch (redisProperties.getMode()) {
      case STANDALONE -> standaloneConfiguration();
      case CLUSTER -> clusterConfiguration();
      case SENTINEL -> sentinelConfiguration();
    };

    LettuceConnectionFactory factory = new LettuceConnectionFactory(
        redisConfiguration,
        clientConfig
    );

    log.info("Initializing Redis connection factory with mode: {}", redisProperties.getMode());
    return factory;
  }

  @Bean
  public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
      LettuceConnectionFactory connectionFactory) {

    RedisSerializer<String> serializer = new StringRedisSerializer();

    RedisSerializationContext<String, String> serializationContext =
        RedisSerializationContext.<String, String>newSerializationContext()
            .key(serializer)
            .value(serializer)
            .hashKey(serializer)
            .hashValue(serializer)
            .build();

    return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
  }

  private ClientOptions clientOptions() {
    return ClientOptions.builder()
        .autoReconnect(true)
        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
        .build();
  }

  private RedisStandaloneConfiguration standaloneConfiguration() {
    RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
    config.setHostName(redisProperties.getHost());
    config.setPort(redisProperties.getPort());
    config.setDatabase(redisProperties.getDatabase());

    if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
      config.setPassword(RedisPassword.of(redisProperties.getPassword()));
    }

    return config;
  }

  private RedisClusterConfiguration clusterConfiguration() {
    List<String> nodes = redisProperties.getCluster().getNodesList();
    if (nodes.isEmpty()) {
      throw new IllegalStateException("Redis cluster nodes are not configured");
    }

    RedisClusterConfiguration config = new RedisClusterConfiguration(nodes);
    config.setMaxRedirects(redisProperties.getCluster().getMaxRedirects());

    if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
      config.setPassword(RedisPassword.of(redisProperties.getPassword()));
    }

    return config;
  }

  private RedisSentinelConfiguration sentinelConfiguration() {
    List<String> sentinelNodes = redisProperties.getSentinel().getNodesList();
    if (sentinelNodes.isEmpty()) {
      throw new IllegalStateException("Redis sentinel nodes are not configured");
    }

    RedisSentinelConfiguration config = new RedisSentinelConfiguration()
        .master(redisProperties.getSentinel().getMaster());

    sentinelNodes.forEach(node -> {
      String[] parts = node.split(":");
      config.sentinel(parts[0], Integer.parseInt(parts[1]));
    });

    config.setDatabase(redisProperties.getDatabase());

    if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
      config.setPassword(RedisPassword.of(redisProperties.getPassword()));
    }

    return config;
  }

  private ClusterClientOptions clusterClientOptions() {
    ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
        .enablePeriodicRefresh(Duration.ofMinutes(10))
        .enableAllAdaptiveRefreshTriggers()
        .build();

    return ClusterClientOptions.builder()
        .autoReconnect(true)
        .topologyRefreshOptions(topologyRefreshOptions)
        .build();
  }
}
