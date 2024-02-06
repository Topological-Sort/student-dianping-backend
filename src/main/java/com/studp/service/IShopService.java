package com.studp.service;

import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Result<Shop> queryShopById(Long id);

    Result<Null> updateShop(Shop shop);
}