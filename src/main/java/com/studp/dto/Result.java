package com.studp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Boolean success;
    private String errorMsg;
    private T data;
    private Long total;

    public static <T> Result<T> ok(){
        return new Result<T>(true, null, null, null);
    }
    public static <T> Result<T> ok(T data){
        return new Result<T>(true, null, data, null);
    }
    public static <T> Result<List<?>> ok(List<?> data, Long total){
        return new Result<>(true, null, data, total);
    }
    public static <T> Result<T> fail(String errorMsg){
        return new Result<T>(false, errorMsg, null, null);
    }
}
