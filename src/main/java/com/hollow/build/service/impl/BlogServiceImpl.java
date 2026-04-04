package com.hollow.build.service.impl;

import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.dto.PageResult;
import com.hollow.build.entity.mysql.BlogPost;
import com.hollow.build.repository.mysql.BlogMapper;
import com.hollow.build.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 博客服务实现类，提供博客的日期菜单查询、按日期/标签/关键词检索、分页查询、增删改等功能。
 */
@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {

    private final BlogMapper blogMapper;

    /**
     * 查询博客的日期归档菜单列表。
     *
     * @return 按日期分组的博客归档菜单
     */
    @Override
    @Cacheable(value = "blog_date_menu")
    public List<BlogDateMenuDto> selectBlogDateMenu() {
        return blogMapper.getBlogDateMenu();
    }

    /**
     * 根据归档月份查询该月内的博客列表。
     *
     * @param date 月份字符串，格式通常为 yyyy-MM
     * @return 该月份下的博客简要信息列表
     */
    @Override
    @Cacheable(value = "blog_date_list", key = "#date")
    public List<BlogDateListDto> selectBlogDateListByDate(String date) {
        // 1. 假设前端传来的 date 格式是 "2023-11"
        YearMonth inputMonth = YearMonth.parse(date);

        // 2. 计算【月初时间】：2023-11-01 00:00:00
        String startDate = inputMonth.atDay(1)
                .atStartOfDay()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 3. 计算【下月月初】：2023-12-01 00:00:00
        // (YearMonth 会自动处理跨年，比如输入 2023-12，这里会自动变成 2024-01)
        String endDate = inputMonth.plusMonths(1)
                .atDay(1)
                .atStartOfDay()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 4. 传入两个参数调用 Mapper
        return blogMapper.getBlogDateListByDate(startDate, endDate);
    }

    /**
     * 根据标签查询博客列表。
     *
     * @param tag 博客标签
     * @return 匹配标签的博客简要信息列表
     */
    @Override
    @Cacheable(value = "blog_tag_list", key = "#tag")
    public List<BlogDateListDto> selectBlogDateListByTag(String tag) {
        return blogMapper.getBlogDateListByTag(tag);
    }

    /**
     * 根据文章 ID 查询博客详情。
     *
     * @param articleId 博客文章 ID
     * @return 对应的博客详情
     */
    @Override
    @Cacheable(value = "blog", key = "#articleId")
    public BlogPost selectBlogById(String articleId) {
        return blogMapper.selectBlogById(articleId);
    }

    /**
     * 根据关键词模糊搜索博客。
     *
     * @param keyWord 搜索关键词
     * @return 匹配关键词的博客简要信息列表
     */
    @Override
    @Cacheable(value = "blog_keyword", key = "#keyWord")
    public List<BlogDateListDto> selectBlogByKeyWord(String keyWord) {
        return blogMapper.selectBlogByKeyWord(keyWord);
    }

    /**
     * 分页查询博客列表。
     *
     * @param page 当前页码
     * @param pageSize 每页条数
     * @param searchName 搜索关键词，可为空
     * @return 博客分页结果
     */
    @Override
    @Cacheable(value = "blog_page", key = "#page + '-' + #pageSize + '-' + #searchName")
    public PageResult<BlogDateListDto> selectBlogByPage(Integer page, Integer pageSize, String searchName) {
        int offset = (page - 1) * pageSize;
        int limit = pageSize;
        List<BlogDateListDto> allBlogInfoByPage = blogMapper.getBlogInfoByPage(offset,limit,searchName);

        return new PageResult<>(allBlogInfoByPage, blogMapper.getCountBlog(searchName));
    }

    /**
     * 新增博客文章。
     *
     * @param blogPost 博客实体数据
     */
    @Override
    @Caching(evict = {
            @CacheEvict(value = "blog_date_menu", allEntries = true),
            @CacheEvict(value = "blog_date_list", allEntries = true),
            @CacheEvict(value = "blog_tag_list", allEntries = true),
            @CacheEvict(value = "blog_page", allEntries = true),
            @CacheEvict(value = "blog_tags", allEntries = true),
            @CacheEvict(value = "blog_keyword", allEntries = true)
    })
    public void addBlog(BlogPost blogPost) {
        blogMapper.addBlogPost(blogPost);
    }

    /**
     * 更新指定博客文章。
     *
     * @param blogId 博客 ID
     * @param blogPost 更新后的博客数据
     */
    @Override
    @Caching(evict = {
            @CacheEvict(value = "blog_date_menu", allEntries = true),
            @CacheEvict(value = "blog_date_list", allEntries = true),
            @CacheEvict(value = "blog_tag_list", allEntries = true),
            @CacheEvict(value = "blog", allEntries = true),
            @CacheEvict(value = "blog_page", allEntries = true),
            @CacheEvict(value = "blog_tags", allEntries = true),
            @CacheEvict(value = "blog_keyword", allEntries = true)
    })
    public void updateBlog(Integer blogId, BlogPost blogPost) {
        blogMapper.updateBlog(blogId, blogPost);
    }

    /**
     * 批量删除博客文章。
     *
     * @param blogPost 包含待删除博客 ID 列表的参数映射
     */
    @Override
    @Caching(evict = {
            @CacheEvict(value = "blog_date_menu", allEntries = true),
            @CacheEvict(value = "blog_date_list", allEntries = true),
            @CacheEvict(value = "blog_tag_list", allEntries = true),
            @CacheEvict(value = "blog", allEntries = true),
            @CacheEvict(value = "blog_page", allEntries = true),
            @CacheEvict(value = "blog_tags", allEntries = true),
            @CacheEvict(value = "blog_keyword", allEntries = true)
    })
    public void deleteBlog(Map<String, Object> blogPost) {
        List<String> blogList = (List<String>)blogPost.get("blogIds");
        if (blogList != null && !blogList.isEmpty()) {
            blogMapper.deleteBlog(blogList);
        }
    }

    /**
     * 查询并整理所有已使用的博客标签。
     *
     * @return 去重排序后的标签列表
     */
    @Override
    @Cacheable(value = "blog_tags")
    public List<String> selectBlogTags() {
        List<String> rawTagsList = blogMapper.selectBlogTags();
        if (rawTagsList == null || rawTagsList.isEmpty()) {
            return Collections.emptyList();
        }
        // 2. 核心处理逻辑
        return rawTagsList.stream()
                .filter(str -> str != null && !str.isEmpty())
                .flatMap(str -> Arrays.stream(str.split(",")))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
