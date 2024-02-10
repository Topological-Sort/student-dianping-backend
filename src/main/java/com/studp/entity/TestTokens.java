package com.studp.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestTokens {
    Long id;
    String token;
    LocalDateTime createTime;
}
