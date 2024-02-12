package com.studp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// 关注分页查询返回结果dto
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScrollResult<T> {
    // 记录
    private List<T> list;
    // 返回结果中的最小时间戳（最远的一条记录）
    private Long minTime;
    // 偏移量
    private Integer offset;
}
