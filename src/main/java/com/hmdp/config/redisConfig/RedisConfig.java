package com.hmdp.config.redisConfig;

import com.hmdp.config.redisConfig.redisLog.RedisLogUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.logging.Logger;

@Configuration
@Import(RedisNode.class)
public class RedisConfig {

    @Autowired
    private RedisProperties redisProperties;

    @Autowired
    private RedisLogUtils log;

    @Bean
    public RedisConnectionFactory redisReadFactoryNode1(){
        RedisNode[] slaves = redisProperties.getSlaves();
        RedisStandaloneConfiguration config=new RedisStandaloneConfiguration();
        config.setHostName(slaves[0].getHost());
        config.setPort(slaves[0].getPort());
        RedisConnectionFactory factory=new LettuceConnectionFactory(config);
        log.redisConnectionfactoryInitializing(factory,slaves[0]);
        return factory;
    }

    @Bean
    public RedisConnectionFactory redisReadFactoryNode2(){
        RedisNode[] slaves = redisProperties.getSlaves();
        RedisStandaloneConfiguration config=new RedisStandaloneConfiguration();
        config.setHostName(slaves[1].getHost());
        config.setPort(slaves[1].getPort());
        LettuceConnectionFactory factory=new LettuceConnectionFactory(config);
        log.redisConnectionfactoryInitializing(factory,slaves[1]);
        return factory;
    }

    @Bean
    public RedisConnectionFactory redisReadFactoryNode3(){
        RedisNode[] slaves = redisProperties.getSlaves();
        RedisStandaloneConfiguration config=new RedisStandaloneConfiguration();
        config.setHostName(slaves[2].getHost());
        config.setPort(slaves[2].getPort());
        LettuceConnectionFactory factory=new LettuceConnectionFactory(config);
        log.redisConnectionfactoryInitializing(factory,slaves[2]);
        return factory;
    }

    @Bean
    @Primary
    public RedisConnectionFactory redisWriteFactoryNode(){
        RedisNode master = redisProperties.getMaster();
        RedisStandaloneConfiguration config=new RedisStandaloneConfiguration();
        config.setHostName(master.getHost());
        config.setPort(master.getPort());
        RedisConnectionFactory factory=new LettuceConnectionFactory(config);
        log.redisConnectionfactoryInitializing(factory,master);
        return factory;
    }


}
