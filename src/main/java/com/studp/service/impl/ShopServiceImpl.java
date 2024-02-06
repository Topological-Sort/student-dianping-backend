package com.studp.service.impl;

import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.entity.Shop;
import com.studp.mapper.ShopMapper;
import com.studp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.utils.CacheClient;
import com.studp.utils.TTLConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final ShopMapper shopMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;

    @Override
    public Result<Shop> queryShopById(Long id) {  // ""cache + Mutex
        Shop shop = cacheClient.queryWithMutex(
                id, Shop.class, this::getById, TTLConstants.TTL, TimeUnit.SECONDS);
        return Result.ok(shop);
    }

    @Override
    public Result<Null> updateShop(Shop shop) {
        /* 先更新mysql，再删除缓存。防止删除缓存并发线程执行查询操作，写入旧缓存 */
        if(shop.getId() == null){
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        shopMapper.updateById(shop);
        // 2.删除缓存（之所以不选择更新缓存，是为了减少无效的写操作，
        //            一旦更新后，缓存又会被覆盖）
        stringRedisTemplate.delete("cache:shop:" + shop.getId());
        return Result.ok();
    }
}
