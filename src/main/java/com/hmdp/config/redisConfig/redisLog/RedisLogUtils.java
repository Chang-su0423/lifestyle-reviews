package com.hmdp.config.redisConfig.redisLog;


import com.hmdp.config.redisConfig.RedisNode;
import com.hmdp.entity.Blog;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
public class RedisLogUtils {
    private static final Logger logger = Logger.getLogger(RedisLogUtils.class.getName());

    public static void redisConnectionfactoryInitializing(RedisConnectionFactory redisConnectionFactory, RedisNode redisNode){
        logger.info(redisNode.getHost()+":"+redisNode.getPort()+"   "+redisConnectionFactory.toString()+"  redisConnectionFactory initialized");
    }

    public static void disableLog(RedisNode targetRedisNode){
        logger.info("redisNode:   "+targetRedisNode.getHost()+":"+targetRedisNode.getPort()+"  was disabled ");
    }

    public static void reAbleLog(RedisNode targetRedisNode){
        logger.info("redisNode:   "+targetRedisNode.getHost()+":"+targetRedisNode.getPort()+"  was reabled ");
    }

    public static void redisTemplateInUseLog(RedisTemplate redisTemplate){
        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();
        LettuceConnectionFactory lettuceConnectionFactory = (LettuceConnectionFactory) connectionFactory;
        logger.info("redisTemplate:   "+redisTemplate.toString()+"  redisNode  "+lettuceConnectionFactory.getHostName()+":"+lettuceConnectionFactory.getPort()+"is in use");
    }

    public static void  redisOffsetLog(RedisNode redisNode,Long offset){
        logger.info("redisNode"+redisNode.getHost()+":"+redisNode.getPort()+"  offset:"+offset);
    }
    public static void scanAvailableSlaveRedisNodes(){
        logger.info("start scan available redisNode >>>");
    }

    public static void occurRedisNodesUnAvailable(RedisNode targetRedisNode){
        logger.info("redisNode"+targetRedisNode.getHost()+":"+targetRedisNode.getPort()+"  unavailable");
    }

    public static void  scanUnAvailableSlaveRedisNodes(){
        logger.info("start scan unavailable redisNode >>>");
    }

    public static void occurRedisNodesAvailable(RedisNode targetRedisNode){
        logger.info("redisNode"+targetRedisNode.getHost()+":"+targetRedisNode.getPort()+" available");
    }
    public static void blogLikeLog(Blog blog){}
}
