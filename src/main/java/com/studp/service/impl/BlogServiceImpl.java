package com.studp.service.impl;

import cn.hutool.db.Db;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studp.dto.Null;
import com.studp.dto.Result;
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
import java.util.List;

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
        Boolean liked = stringRedisTemplate.opsForSet()
                .isMember(blogLikedKey, userId);
        if(Boolean.FALSE.equals(liked)){ // 没有点过赞
            stringRedisTemplate.opsForSet()  // 标记该用户已经点过赞
                    .add(blogLikedKey, userId);
            this.lambdaUpdate()   // 博客的点赞数 + 1
                    .setSql("liked = liked + 1")
                    .eq(Blog::getId, id)
                    .update();
        } else {  // 点过赞，取消点赞
            stringRedisTemplate.opsForSet()
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
        Boolean isLike = stringRedisTemplate.opsForSet()
                .isMember(blogLikedKey, userId); // 判断当前用户是否点赞
        blog.setIsLike(isLike);  // 设置是否点赞字段
        return Result.ok(blog);
    }

    @Override
    public Result<List<Blog>> pageQueryHotBlog(Integer current) {
        // 根据用户分页查询，按点赞数量降序返回
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询博主信息并设置，查询当前登录用户是否点赞该博客
        String id = UserHolder.getUser().getId().toString();  // 用户id
        records.forEach(blog ->{
            Long userId = blog.getUserId();  // 博主id
            User user = userMapper.selectById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            blog.setIsLike(stringRedisTemplate.opsForSet()
                    .isMember(RedisConstants.BLOG_LIKED_KEY + blog.getId().toString(), id));
        });
        return Result.ok(records);
    }
}
