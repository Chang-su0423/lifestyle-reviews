package com.hmdp.controller;


import com.hmdp.config.redisConfig.RedisConnectionFactoryManagement;
import com.hmdp.config.redisConfig.RedisNode;
import com.hmdp.dto.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
public class TestController {

    @Autowired
    private RedisConnectionFactoryManagement redisConnectionFactoryManagement;

    @GetMapping("/test1")
    public Result getTest() {
        // 显式调用 getReadRedisTemplate() 来动态创建 RedisTemplate
        RedisTemplate<String, String> template = redisConnectionFactoryManagement.getReadRedisTemplate();
        String value = template.opsForValue().get("test");
        return Result.ok(value);
    }
    @GetMapping("/test2")
    public Result getTest2() {
        RedisNode redisNode=new RedisNode();
        redisNode.setHost("192.168.50.132");
        redisNode.setPort(6379);
        redisConnectionFactoryManagement.disable(redisNode);
        return Result.ok();
    }
    @GetMapping("/test3")
    public Result getTest3() {
        RedisNode redisNode=new RedisNode();
        redisNode.setHost("192.168.50.132");
        redisNode.setPort(6379);
        redisConnectionFactoryManagement.reAble(redisNode);
        return Result.ok();
    }
}