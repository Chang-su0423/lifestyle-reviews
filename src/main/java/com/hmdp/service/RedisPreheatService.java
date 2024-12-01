package com.hmdp.service;

import org.springframework.stereotype.Component;

@Component
public interface RedisPreheatService {
    void preHeatRedisPipeline();
}
