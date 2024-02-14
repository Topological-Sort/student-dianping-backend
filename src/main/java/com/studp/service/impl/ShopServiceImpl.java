package com.studp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studp.dto.Void;
import com.studp.dto.Result;
import com.studp.entity.Shop;
import com.studp.mapper.ShopMapper;
import com.studp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.utils.CacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.studp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.studp.utils.RedisConstants.SHOP_GEO_KEY;
import static com.studp.utils.SystemConstants.DEFAULT_PAGE_SIZE;

@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final ShopMapper shopMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheClient cacheClient;

    @Override
    public Result<Shop> queryShopById(Long id) {  // ""cache + Mutex
        // 防缓存击穿：若缓存失效，所有线程对mysql的查询互斥
        Shop shop = cacheClient.queryWithMutex(
                id, Shop.class, this::getById,
                CACHE_SHOP_TTL, TimeUnit.SECONDS);
        return Result.ok(shop);
    }

    @Override
    public Result<Void> updateShop(Shop shop) {
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

    @Override
    public Result<List<Shop>> pageQueryShopByType(Integer typeId, Integer current, Double x, Double y) {
        /* 1.不需要按位置查询，直接查询返回 */
        if (x == null || y == null) {
            Page<Shop> page = shopMapper.selectPage(
                    new Page<>(current, DEFAULT_PAGE_SIZE), // 5
                    new LambdaQueryWrapper<Shop>().eq(Shop::getTypeId, typeId) );
            List<Shop> shops = page.getRecords();
            return Result.ok(shops);
        }
        /* 2.计算分页参数，查询redis，GeoResult：[shopId、distance] */
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate
            .opsForGeo().search(
                key, GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
            );
        if (results == null){    // 该类别下无店铺
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent(); // 转为list形式
        if (list.size() <= from) {  // 没有下一页了
            return Result.ok(Collections.emptyList());
        }
        /* 3.分页查询，截取from ~ end的记录 */
        List<Long> ids = new ArrayList<>(list.size());  // 店铺id
        Map<Long, Distance> distanceMap = new HashMap<>(list.size()); // 店铺id -> 距离
        list.stream().skip(from).forEach(result -> {  // 获取从from开始的记录
            // 3.1 获取店铺id
            String shopIdStr = result.getContent().getName();
            Long shopId = Long.valueOf(shopIdStr);
            ids.add(shopId);
            // 3.2 获取到店铺的距离
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });
        // 3.3 根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = shopMapper.selectList(
                new LambdaQueryWrapper<Shop>()
                        .in(Shop::getId, ids)
                        .last("ORDER BY FIELD(id, " + idStr + ")") );
        // 3.4 添加距离字段
        shops.forEach(shop -> shop.setDistance(distanceMap.get(shop.getId()).getValue()) );
        return Result.ok(shops);
    }
}
