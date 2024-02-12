package com.studp.mapper;

import com.studp.entity.Shop;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ShopMapper extends BaseMapper<Shop> {

    @Select("select distinct type_id from tb_shop")
    List<Long> selectDistinctTypeIds();

    @Select("select id, name, type_id, images, area, address, x, y, avg_price, sold," +
            " comments, score, open_hours, create_time, update_time" +
            " from tb_shop")
    List<Shop> list();
}
