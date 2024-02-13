package com.studp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.dto.ScrollResult;
import com.studp.dto.UserDTO;
import com.studp.entity.Blog;
import com.studp.entity.Follow;
import com.studp.entity.User;
import com.studp.mapper.BlogMapper;
import com.studp.mapper.FollowMapper;
import com.studp.mapper.UserMapper;
import com.studp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.utils.RedisConstants;
import com.studp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.studp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.studp.utils.RedisConstants.FEED_ZSET_KEY;
import static com.studp.utils.SystemConstants.MAX_PAGE_SIZE;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    BlogMapper blogMapper;
    @Resource
    UserMapper userMapper;
    @Resource
    FollowMapper followMapper;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<Null> likeBlog(Long id) {
        String blogLikedKey = RedisConstants.BLOG_LIKED_KEY + id.toString();
        String userId = UserHolder.getUser().getId().toString();
        // 判断当前用户是否已经为这个博客点过赞（Z-SET -- "blogId": { [userId,score], ...}）
        // 对应键的 score 是否存在
        boolean liked = stringRedisTemplate.opsForZSet().score(blogLikedKey, userId) != null;
        if(!liked){ // 没有点过赞
            stringRedisTemplate.opsForZSet()  // 标记该用户已经点过赞，分数为当前系统时间
                    .add(blogLikedKey, userId, System.currentTimeMillis());
            this.lambdaUpdate()   // 博客的点赞数 + 1
                    .setSql("liked = liked + 1")
                    .eq(Blog::getId, id)
                    .update();
        } else {  // 点过赞，取消点赞
            stringRedisTemplate.opsForZSet()
                    .remove(blogLikedKey, userId);
            this.lambdaUpdate()   // 博客的点赞数 - 1
                    .setSql("liked = liked - 1")
                    .eq(Blog::getId, id)
                    .update();
        }
        return Result.ok();
    }

    @Override
    public Result<Blog> getBlogById(Long id) {
        String blogLikedKey = RedisConstants.BLOG_LIKED_KEY + id;
        String userId = UserHolder.getUser().getId().toString();
        // 查询blog
        Blog blog = blogMapper.selectById(id);
        Double isLike = stringRedisTemplate.opsForZSet()
                .score(blogLikedKey, userId); // 判断当前用户是否点赞
        blog.setIsLike(isLike != null);    // 设置是否点赞字段
        return Result.ok(blog);
    }

    @Override
    public Result<List<Blog>> pageQueryHotBlog(Integer current) {
        // 根据用户分页查询，按点赞数量降序返回
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询博主信息并设置，查询当前登录用户是否点赞该博客
        String id = UserHolder.getUser() != null ?  // 当前用户已登录，则设置用户id
                UserHolder.getUser().getId().toString() : null;
        records.forEach(blog -> {
            Long userId = blog.getUserId();  // 博主id
            User user = userMapper.selectById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            blog.setIsLike(id != null && stringRedisTemplate.opsForZSet()
                    .score(RedisConstants.BLOG_LIKED_KEY + blog.getId().toString(), id) != null);
        });
        return Result.ok(records);
    }

    @Override
    public Result<List<UserDTO>> queryBlogLikes(Long id) {
        String blogLikesKey = RedisConstants.BLOG_LIKED_KEY + id.toString();
        // 获取前五个点赞的用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(blogLikesKey, 0, 5);
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());  // 该博客没有点赞用户
        }
        // 获取前五个点赞的用户id 和信息
        List<Long> ids = top5.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
        List<UserDTO> userDTOS = userMapper.selectBatchIds(ids)
                .stream()
                .map((o) -> BeanUtil.copyProperties(o, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result<List<Blog>> pageQueryUserBlog(Integer current, Long id) {
        Page<Blog> page = blogMapper.selectPage(
                new Page<>(current, MAX_PAGE_SIZE),
                new LambdaQueryWrapper<Blog>().eq(Blog::getUserId, id));
        List<Blog> blogs = page.getRecords();
        return Result.ok(blogs);
    }

    @Override
    public Result<Long> saveBlog(Blog blog) {
        if (blog.getShopId() == null) {
            return Result.fail("关联商户不能为空");
        }
        if(blog.getTitle() == null) {
            return Result.fail("请输入标题");
        }
        /* 1.保存当前用户发的博客 */
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        this.save(blog);  // 保存后自动填充id（自增） blogMapper.insert(blog)不会自动填充id
        /* 2.将该博客 id 推送到所有关注了该用户的粉丝的 Z-SET 中 */
        List<Long> ids = followMapper.selectList(  // 查询关注了该博主的用户id
                new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getFollowUserId, userId))
                .stream()
                .map(Follow::getUserId)
                .collect(Collectors.toList());
        ids.forEach(id -> {    // 存储到所有粉丝的 z-set中
            String feedKey = FEED_ZSET_KEY + id.toString();
            stringRedisTemplate.opsForZSet().add(feedKey, userId.toString(), System.currentTimeMillis());
        });
        return Result.ok(blog.getId()); // 返回博客id
    }

    @Override
    public Result<ScrollResult<Blog>> pageQueryBlogOfFollow(Long max, Integer offset) {
        // max == 上一次返回的ScrollResult中的 minTime 最小时间戳，即最远(最下面)记录对应的时间戳
        /* 1.获取当前登录用户的关注列表 Z-SET */
        String userId = UserHolder.getUser().getId().toString();
        String feedKey = FEED_ZSET_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate
                .opsForZSet()  // 按倒序返回结果，参数：(K key, double min, double max, long offset, long count)
                .reverseRangeByScoreWithScores(feedKey, 0, max, offset, 2);  // scores \in [min, max](注意max是闭区间，所以需要offset), offset: 偏移量, count: 最多返回多少个（为-1返回所有）
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        /* 2.获取id、最远时间戳和偏移量 */
        long minTime = 0;  // 最远的一条记录的时间戳
        int ofs = 1; // offset 偏移量
        List<Long> ids = new ArrayList<>(typedTuples.size());  // 所有已关注的博主id
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            Long id = Long.parseLong(tuple.getValue());
            ids.add(id);
            long time = tuple.getScore().longValue();
            if (time == minTime) ofs++;   // 相同记录，偏移量+1
            else { minTime = time; ofs = 1; }   // minTime < time
        }
        /* 3.查询对应博客并返回 */
        String idsStr = StrUtil.join(",", ids);  // 按id的顺序查询
        List<Blog> blogs = blogMapper.selectList(
                new LambdaQueryWrapper<Blog>()
                        .in(Blog::getId, ids)
                        .last("ORDER BY FIELD(id, " + idsStr + ")") );
        for (Blog blog : blogs) {
            Long id = blog.getId();
            boolean liked = stringRedisTemplate.opsForZSet()
                    .score(BLOG_LIKED_KEY + id.toString(), id.toString()) != null;
            blog.setIsLike(liked);  // 设置是否点赞
        }
        ScrollResult<Blog> res = new ScrollResult<>(blogs, minTime, ofs);
        return Result.ok(res);
    }
}
