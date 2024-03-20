package com.studp.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)  // 用作方法
@Retention(RetentionPolicy.RUNTIME)  // 执行时注解
public @interface RecordTime {

}
