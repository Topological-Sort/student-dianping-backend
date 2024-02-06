package com.studp.entity;

import lombok.Data;

import java.time.LocalDateTime;

// 附带逻辑过期时间的Redis存储对象
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
