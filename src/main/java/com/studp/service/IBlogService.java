package com.studp.service;

import com.studp.dto.Null;
import com.studp.dto.Result;
import com.studp.dto.ScrollResult;
import com.studp.dto.UserDTO;
import com.studp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.studp.entity.Follow;

import java.util.List;

public interface IBlogService extends IService<Blog> {

    Result<Null> likeBlog(Long id);

    Result<Blog> getBlogById(Long id);

    Result<List<Blog>> pageQueryHotBlog(Integer current);

    Result<List<UserDTO>> queryBlogLikes(Long id);

    Result<List<Blog>> pageQueryUserBlog(Integer current, Long id);

    Result<Long> saveBlog(Blog blog);

    Result<ScrollResult<Blog>> pageQueryBlogOfFollow(Long max, Integer offset);
}
