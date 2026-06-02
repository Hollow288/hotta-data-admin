package com.hollow.build.blog;

import com.hollow.build.blog.BlogDateListDto;
import com.hollow.build.blog.BlogDateMenuDto;
import com.hollow.build.common.PageResult;
import com.hollow.build.blog.BlogPost;

import java.util.List;
import java.util.Map;

/**
 * 博客服务接口，提供博客文章的增删改查及分类检索功能
 */
public interface BlogService {

    /**
     * 查询博客日期菜单列表，按日期分组展示
     *
     * @return 博客日期菜单DTO列表
     */
    List<BlogDateMenuDto> selectBlogDateMenu();

    /**
     * 根据日期查询博客文章列表
     *
     * @param date 日期字符串
     * @return 该日期下的博客列表DTO
     */
    List<BlogDateListDto> selectBlogDateListByDate(String date);

    /**
     * 根据标签查询博客文章列表
     *
     * @param tag 标签名称
     * @return 该标签下的博客列表DTO
     */
    List<BlogDateListDto> selectBlogDateListByTag(String tag);

    /**
     * 根据文章ID查询博客详情
     *
     * @param articleId 文章ID
     * @return 博客文章实体
     */
    BlogPost selectBlogById(String articleId);

    /**
     * 根据关键词搜索博客文章
     *
     * @param keyWord 搜索关键词
     * @return 匹配的博客列表DTO
     */
    List<BlogDateListDto> selectBlogByKeyWord(String keyWord);

    /**
     * 分页查询博客文章列表
     *
     * @param page 当前页码
     * @param pageSize 每页数量
     * @param searchName 搜索关键词
     * @return 分页结果
     */
    PageResult<BlogDateListDto> selectBlogByPage(Integer page, Integer pageSize, String searchName);

    /**
     * 新增博客文章
     *
     * @param blogPost 博客文章实体
     */
    void addBlog(BlogPost blogPost);

    /**
     * 更新博客文章
     *
     * @param blogId 博客ID
     * @param blogPost 更新后的博客文章实体
     */
    void updateBlog(Integer blogId, BlogPost blogPost);

    /**
     * 删除博客文章
     *
     * @param blogPost 包含待删除博客信息的Map
     */
    void deleteBlog(Map<String, Object> blogPost);

    /**
     * 查询所有博客标签
     *
     * @return 标签名称列表
     */
    List<String> selectBlogTags();
}
