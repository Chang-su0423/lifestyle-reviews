package com.hmdp.utils;

import com.hmdp.utils.interf.ILock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock
{
    private StringRedisTemplate redis;
    private static final String PREFIX_NAME="lock:" ;
    private String name;
    private static final String THREAD_ID_PREFIX= UUID.randomUUID().toString()+"-";
    private static final DefaultRedisScript<Long> REDIS_SCRIPT;
    static
    {
        REDIS_SCRIPT=new DefaultRedisScript<>();
        REDIS_SCRIPT.setResultType(Long.class);
        REDIS_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    }
    public SimpleRedisLock(StringRedisTemplate redis, String name) {
        this.redis = redis;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeOutSec) {
        String threadId= THREAD_ID_PREFIX+Thread.currentThread().getId();
        Boolean success = redis.opsForValue().setIfAbsent(PREFIX_NAME + name, threadId , timeOutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //调用LUA脚本
        Long executeId = redis.execute(
                REDIS_SCRIPT,
                Collections.singletonList(PREFIX_NAME + name),
                THREAD_ID_PREFIX + Thread.currentThread().getId()
        );
    }
}
