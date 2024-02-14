package com.studp;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studp.dto.LoginFormDTO;
import com.studp.dto.Result;
import com.studp.entity.Shop;
import com.studp.entity.TestTokens;
import com.studp.mapper.ShopMapper;
import com.studp.mapper.TestTokensMapper;
import com.studp.service.IBlogService;
import com.studp.service.IShopService;
import com.studp.service.IUserService;
import com.studp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.studp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest(classes = StudentDianPingApplication.class)
public class StudentDianPingTest {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testConnect() {
        stringRedisTemplate.opsForValue().set("name", "jack");
        System.out.println(stringRedisTemplate.opsForValue().get("name"));
    }

    @Test
    void testStream() {
        for (int i = 0; i < 10; i++) {
            List<MapRecord<String, Object, Object>> list =
                    stringRedisTemplate.opsForStream()
                            .read(Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed()));
            if (list == null || list.isEmpty()) {
                continue;
            }
            MapRecord<String, Object, Object> entries = list.get(0);
            Map<Object, Object> value = entries.getValue();
            System.out.println(value);
        }
    }

    // 保存店铺地址位置信息到redis
    @Resource
    ShopMapper shopMapper;
    @Test
    void saveShopGEO(){
        /* 1.根据类别将不同店铺分组 Map[typeId -> List<Shop>] */
        List<Shop> total = shopMapper.list();
        Map<Long, List<Shop>> shopMap = total.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shops = entry.getValue();
            /* 2.将同类别的店铺保存在同一个GEO中:  typeId -> GEO[shopId, Point(x,y)] */
            String geoKey = SHOP_GEO_KEY + typeId.toString();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
//                stringRedisTemplate.opsForGeo().add(  // 方法一
//                        geoKey, new Point(shop.getX(), shop.getY()), shop.getId().toString() );
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(geoKey, locations);
        }
    }

    @Test
    void testHyperLogLog(){  // 测试unique visitor统计（用户活跃度 / 博客热度）
        String[] users = new String[1000];
        int k = 0;
        // 10万用户进行共200万次访问
        for (int i = 1; i <= 2000000; i++){
            users[k++] = "user_" + i % 100000;  // 模拟用户id
            if (i % 1000 == 0) {  // 每1000条记录一下
                k = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
            }
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("The number of unique visitor: "+size);
    }

    // 生成测试用token，再在mysql中导出csv/txt格式备用
    @Resource
    IUserService userService;
    @Resource
    TestTokensMapper testTokensMapper;
    @Test
    void generateTokens(){
        Random r = new Random();
        for(int i = 0; i < 1200; i++) {
            LoginFormDTO form = new LoginFormDTO();
            form.setPhone("131" + RandomUtil.randomNumbers(8));
            Result<String> res =  userService.testLogin(form);
            String token = res.getData();
            TestTokens testTokens = new TestTokens();
            testTokens.setToken(token);
            testTokensMapper.insert(testTokens);
        }
    }

    // 测试数据
    @Test
    void addTestShops(){
        for (int i = 0; i < 10000; i++) {
            Shop shop = Shop.builder().area("beijing").address("123").x(10.1000).y(20.0000).avgPrice(30L)
                    .name("1234").comments(47).score(47).sold(32).typeId(3L).openHours("123")
                    .build();
            shopMapper.insert(shop);
        }
    }
}
