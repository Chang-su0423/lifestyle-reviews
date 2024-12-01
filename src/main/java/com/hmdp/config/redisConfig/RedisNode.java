package com.hmdp.config.redisConfig;


import lombok.Data;

@Data
public class RedisNode {

    private String host;

    private int port;

    private String name;

    public RedisNode(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public RedisNode() {
    }
}
