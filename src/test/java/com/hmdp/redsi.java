package com.hmdp;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
public class redsi {

    @Autowired
    private RedisTemplate<String,String> readRedisTemplate;
    @Test
    public void test()
    {
        readRedisTemplate.opsForValue().get("test");
    }
}
