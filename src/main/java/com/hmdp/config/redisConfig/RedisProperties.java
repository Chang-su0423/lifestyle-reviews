package com.hmdp.config.redisConfig;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Data
@Component
@ConfigurationProperties(prefix = "spring.redis")

public class RedisProperties {

    private RedisNode master;

    private RedisNode[] slaves;

    private Long maxOffsetMinus;
 }
