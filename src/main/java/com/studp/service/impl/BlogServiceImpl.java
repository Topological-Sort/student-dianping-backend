package com.studp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.Db;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.dto.UserDTO;
import com.studp.entity.Blog;
import com.studp.entity.User;
import com.studp.mapper.BlogMapper;
import com.studp.mapper.UserMapper;
import com.studp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.studp.utils.RedisConstants;
import com.studp.utils.SystemConstants;
import com.studp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    BlogMapper blogMapper;
    @Resource
    UserMapper userMapper;
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result<Null> likeBlog(Long id) {
        String blogLikedKey = RedisConstants.BLOG_LIKED_KEY + id;
        String userId = UserHolder.getUser().getId().toString();
        // 判断当前用户是否已经为这个博客点过赞（SET -- "blogId": {userId ...}）
        // SISMEMBER key userId
        Double liked = stringRedisTemplate.opsForZSet().score(blogLikedKey, userId);
        if(liked == null){ // 没有点过赞
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
                new Page<Blog>(current, MAX_PAGE_SIZE),
                new LambdaQueryWrapper<Blog>()
                        .eq(Blog::getUserId, id));
        List<Blog> blogs = page.getRecords();
        return Result.ok(blogs);
    }
}
