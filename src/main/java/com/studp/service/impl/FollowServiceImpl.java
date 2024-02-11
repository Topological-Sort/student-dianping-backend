package com.studp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.Follow;
import com.studp.mapper.FollowMapper;
import com.studp.mapper.UserMapper;
import com.studp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.studp.utils.RedisConstants.FOLLOW_SET;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    FollowMapper followMapper;
    @Resource
    UserMapper userMapper;
    @Resource
    StringRedisTemplate stringRedisTemplate;  // 实现共同关注功能

    @Override
    public Result<Null> follow(Long id, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {  // 关注
            Follow follow = Follow.builder()
                    .userId(userId)
                    .followUserId(id).build();
            followMapper.insert(follow);
        } else {  // 取关
            followMapper.delete(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, id));
        }
        /* 为了实现共同关注功能，更新当前userId的Set缓存 */
        String key = FOLLOW_SET + userId.toString();
        String value = id.toString();
        if (isFollow)
            stringRedisTemplate.opsForSet().add(key, value);
        else
            stringRedisTemplate.opsForSet().remove(key, value);
        return Result.ok();
    }

    @Override
    public Result<Boolean> isFollow(Long id) {
        Long userId = UserHolder.getUser().getId();
        int count = followMapper.selectCount(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, id));
        if(count == 0)
            return Result.ok(false);
        else
            return Result.ok(true);
    }

    @Override
    public Result<List<UserDTO>> commonFollow(Long id) {
        String key1 = FOLLOW_SET + UserHolder.getUser().getId().toString(); // 当前登录的用户key
        String key2 = FOLLOW_SET + id.toString(); // 点进去看到的用户key
        Set<String> set = stringRedisTemplate.opsForSet()
                .intersect(List.of(key1, key2));// 获取交集
        if(set == null || set.isEmpty())
            return Result.ok();
        List<Long> ids = set.stream() // id类型转换
                .map(Long::parseLong).collect(Collectors.toList());
        List<UserDTO> userDTOS = BeanUtil.copyToList(
                userMapper.selectBatchIds(ids), UserDTO.class);
        return Result.ok(userDTOS);
    }

}
