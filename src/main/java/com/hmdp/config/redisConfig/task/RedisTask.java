package com.hmdp.config.redisConfig.task;


import com.hmdp.config.redisConfig.RedisOffsetMonitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RedisTask {

    @Autowired
    private RedisOffsetMonitor redisOffsetMonitor;

    @Autowired
    private BlogLikedTask blogLikedTask;

    @Scheduled(fixedDelay = 5000) // 每5秒执行一次，但会等待上次任务完成后再延迟5秒
    public void offsetTask() {
        redisOffsetMonitor.scanAvailableSlaveRedisNodes();
        redisOffsetMonitor.scanUnAvailableSlaveRedisNodes();
    }

    @Scheduled(cron = "0 0 4 * * ?")// 每一分钟执行一次，但会等待上次任务完成后再延迟一分钟
    public void redisLikedTask() {
        blogLikedTask.scanLikedZset();
    }

}
