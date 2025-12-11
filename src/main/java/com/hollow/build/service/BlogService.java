package com.hollow.build.service;

import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.dto.PageResult;
import com.hollow.build.entity.mysql.BlogPost;

import java.util.List;
import java.util.Map;

public interface BlogService {
    List<BlogDateMenuDto> selectBlogDateMenu();

    List<BlogDateListDto> selectBlogDateListByDate(String date);

    List<BlogDateListDto> selectBlogDateListByTag(String tag);

    BlogPost selectBlogById(String articleId);

    List<BlogDateListDto> selectBlogByKeyWord(String keyWord);

    PageResult<BlogDateListDto> selectBlogByPage(Integer page, Integer pageSize, String searchName);

    void addBlog(BlogPost blogPost);

    void updateBlog(Integer blogId, BlogPost blogPost);

    void deleteBlog(Map<String, Object> blogPost);

    List<String> selectBlogTags();
}
