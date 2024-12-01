package com.hmdp;


import com.hmdp.config.redisConfig.task.BlogLikedTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ScanShopLikedNumsTest {

    @Autowired
    private BlogLikedTask blogLikedTask;

    @Test
    public void test()
    {
        blogLikedTask.scanLikedZset();
    }
}
