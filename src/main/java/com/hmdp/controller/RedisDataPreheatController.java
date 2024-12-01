package com.hmdp.controller;


import com.hmdp.config.redisConfig.RedisConnectionFactoryManagement;
import com.hmdp.entity.Shop;
import com.hmdp.service.RedisPreheatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@RestController
@RequestMapping("/data/preheat")
public class RedisDataPreheatController {

    @Autowired
    private RedisPreheatService redisPreheatService;

    @GetMapping("/sc0423")
    public void preHeatRedisData(){
        redisPreheatService.preHeatRedisPipeline();
    }
}
