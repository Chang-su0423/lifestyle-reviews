package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;
    @Test
    public void test()
    {
        shopService.saveShopToRedis(1L,2L);
        shopService.saveShopToRedis(2L,2L);
        shopService.saveShopToRedis(3L,2L);
        shopService.saveShopToRedis(4L,2L);
        shopService.saveShopToRedis(5L,2L);
        shopService.saveShopToRedis(6L,2L);
        shopService.saveShopToRedis(7L,2L);
        shopService.saveShopToRedis(8L,2L);
        shopService.saveShopToRedis(9L,2L);
        shopService.saveShopToRedis(10L,2L);
        shopService.saveShopToRedis(11L,2L);
        shopService.saveShopToRedis(12L,2L);
        shopService.saveShopToRedis(13L,2L);
        shopService.saveShopToRedis(14L,2L);

    }


}
