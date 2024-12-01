package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootTest
public class InsertDataBase {

    @Autowired
    private ShopMapper shopMapper;  // 注入 MyBatis Mapper

    @Test
    public void testInsertShop() {
        // 创建 Shop 对象并插入数据库
        for (int i = 1000; i < 2000; i++) {
        Shop shop = new Shop();
        shop.setName("Test Shop"+i);
        shop.setTypeId(1L);
        shop.setArea("Test Area"+i);
        shop.setAddress("Test Address"+i);
        shop.setX(120.0);
        shop.setY(30.0);
        shop.setAvgPrice(100L);
        shop.setSold(5000);
        shop.setComments(200);
        shop.setScore(5);
        shop.setOpenHours("10:00-22:00");
        shop.setImages(i+"");
        shopMapper.insert(shop);

        }


    }

}
