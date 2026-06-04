package com.hollow.build.blog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 博客文章 MyBatis Mapper 接口，提供博客相关的数据库操作。
 */
@Mapper
public interface BlogMapper {

    /**
     * 获取博客按日期分组的菜单列表。
     *
     * @return 博客日期菜单列表
     */
    List<BlogDateMenuDto> getBlogDateMenu();

    /**
     * 根据日期范围查询博客列表。
     *
     * @param startDate 起始日期
     * @param endDate   结束日期
     * @return 指定日期范围内的博客列表
     */
    List<BlogDateListDto> getBlogDateListByDate(@Param("startDate") String startDate,
                                                @Param("endDate") String endDate);

    /**
     * 根据标签查询博客列表。
     *
     * @param tag 标签名称
     * @return 包含指定标签的博客列表
     */
    List<BlogDateListDto> getBlogDateListByTag(String tag);

    /**
     * 根据文章 ID 查询博客详情。
     *
     * @param articleId 文章 ID
     * @return 博客文章对象，未找到时返回 null
     */
    BlogPost selectBlogById(String articleId);

    /**
     * 根据关键词搜索博客列表。
     *
     * @param keyWord 搜索关键词
     * @return 匹配关键词的博客列表
     */
    List<BlogDateListDto> selectBlogByKeyWord(String keyWord);

    /**
     * 获取博客总数（支持按名称搜索）。
     *
     * @param searchName 搜索关键词，可为空
     * @return 博客总数
     */
    Integer getCountBlog(@Param("searchName") String searchName);

    /**
     * 分页查询博客列表。
     *
     * @param offset     偏移量
     * @param limit      每页数量
     * @param searchName 搜索关键词，可为空
     * @return 分页后的博客列表
     */
    List<BlogDateListDto> getBlogInfoByPage(@Param("offset") int offset, @Param("limit") int limit, @Param("searchName") String searchName);

    /**
     * 新增博客文章。
     *
     * @param blogPost 博客文章对象
     */
    void addBlogPost(BlogPost blogPost);

    /**
     * 更新指定 ID 的博客文章。
     *
     * @param blogId   博客 ID
     * @param blogPost 更新后的博客文章对象
     */
    void updateBlog(@Param("blogId") Integer blogId, @Param("blogPost") BlogPost blogPost);

    /**
     * 批量删除博客文章。
     *
     * @param blogList 待删除的博客 ID 列表
     */
    void deleteBlog(List<String> blogList);

    /**
     * 查询所有博客标签。
     *
     * @return 博客标签列表
     */
    List<String> selectBlogTags();
}
