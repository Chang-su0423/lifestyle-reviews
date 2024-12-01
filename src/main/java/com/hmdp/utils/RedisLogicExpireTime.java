package com.hmdp.utils;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisLogicExpireTime
{
    private LocalDateTime expireTime;
    private Object data;

    public RedisLogicExpireTime(LocalDateTime expireTime, Object data) {
        this.expireTime = expireTime;
        this.data = data;
    }

    public RedisLogicExpireTime() {
    }
}
