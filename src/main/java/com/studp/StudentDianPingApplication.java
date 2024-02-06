package com.studp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.studp.mapper")
@SpringBootApplication
public class StudentDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentDianPingApplication.class, args);
    }

}
