package com.studp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

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

}
