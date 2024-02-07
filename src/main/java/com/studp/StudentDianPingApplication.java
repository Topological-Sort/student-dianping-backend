package com.studp;

import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@MapperScan("com.studp.mapper")
@SpringBootApplication
@EnableKnife4j
@EnableAspectJAutoProxy(exposeProxy = true)
public class StudentDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentDianPingApplication.class, args);
    }

}
