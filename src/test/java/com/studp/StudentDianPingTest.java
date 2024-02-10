package com.studp;

import cn.hutool.core.util.RandomUtil;
import com.studp.dto.LoginFormDTO;
import com.studp.dto.Result;
import com.studp.entity.TestTokens;
import com.studp.mapper.ShopMapper;
import com.studp.mapper.TestTokensMapper;
import com.studp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

//    // 生成测试用token，redis中不存在的token需要在mysql中去掉，再在mysql中导出csv/txt格式备用
//    @Resource
//    IUserService userService;
//    @Resource
//    TestTokensMapper testTokensMapper;
//    @Test
//    public void generateTokens(){
//        Random r = new Random();
//        for(int i = 0; i < 500; i++) {
//            LoginFormDTO form = new LoginFormDTO();
//            form.setPhone("131" + RandomUtil.randomNumbers(8));
//            Result<String> res =  userService.login(form);
//            String token = res.getData();
//            TestTokens testTokens = new TestTokens();
//            testTokens.setToken(token);
//            testTokensMapper.insert(testTokens);
//        }
//    }
}
