package com.hollow.build.blog;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.ratelimit.BypassRateLimit;
import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


/**
 * 博客控制器，提供博客的增删改查及分页检索等功能
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/blog")
@Tag(name = "博客", description = "博客")
public class BlogController {

    private final BlogService BlogService;

    /**
     * 查询博客按日期分组的菜单，包含每个日期对应的博客数量
     *
     * @return 博客日期菜单列表
     */
    @GetMapping("/blog-date-menu")
    @Operation(
            summary = "查询博客日期/数量",
            description = "查询博客日期/数量"
    )
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<BlogDateMenuDto>> selectBlogDateMenu(){
        return ApiResponse.success(BlogService.selectBlogDateMenu());
    }

    /**
     * 查询所有博客标签
     *
     * @return 博客标签列表
     */
    @GetMapping("/blog-date-tags")
    @Operation(
            summary = "查询博客标签",
            description = "查询博客标签"
    )
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<String>> selectBlogTags(){
        return ApiResponse.success(BlogService.selectBlogTags());
    }

    /**
     * 根据日期查询该日期下的博客列表
     *
     * @param date 日期字符串
     * @return 该日期下的博客列表
     */
    @GetMapping("/blog-date-list/{date}")
    @Operation(
            summary = "根据日期查询博客列表",
            description = "根据日期查询博客列表"
    )
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<BlogDateListDto>> selectBlogDateListByDate(@PathVariable("date") String date){
        return ApiResponse.success(BlogService.selectBlogDateListByDate(date));
    }

    /**
     * 根据标签查询博客列表
     *
     * @param tag 博客标签
     * @return 该标签下的博客列表
     */
    @GetMapping("/blog-tag/{tag}")
    @Operation(
            summary = "根据标签查询博客列表",
            description = "根据标签查询博客列表"
    )
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<BlogDateListDto>> selectBlogDateListByTag(@PathVariable("tag") String tag){
        return ApiResponse.success(BlogService.selectBlogDateListByTag(tag));
    }

    /**
     * 根据博客ID查询博客详细内容
     *
     * @param articleId 博客文章ID
     * @return 博客详细信息
     */
    @GetMapping("/{articleId}")
    @Operation(
            summary = "根据ID查询博客",
            description = "根据ID查询博客"
    )
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<BlogPost> selectBlogById(@PathVariable("articleId") String articleId){
        return ApiResponse.success(BlogService.selectBlogById(articleId));
    }

    /**
     * 根据关键词搜索博客
     *
     * @param keyWord 搜索关键词
     * @return 匹配关键词的博客列表
     */
    @GetMapping("/search/{keyWord}")
    @Operation(
            summary = "根据关键词查询博客",
            description = "根据关键词查询博客"
    )
    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<BlogDateListDto>> selectBlogByKeyWord(@PathVariable("keyWord") String keyWord){
        return ApiResponse.success(BlogService.selectBlogByKeyWord(keyWord));
    }

    /**
     * 分页查询博客列表，支持按名称搜索（需管理员权限）
     *
     * @param page 页码
     * @param pageSize 每页数量
     * @param searchName 搜索名称（可选）
     * @return 分页博客列表
     */
    @GetMapping("/page/search")
    @Operation(
            summary = "根据关键词分页查询博客",
            description = "根据关键词分页查询博客"
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<BlogDateListDto>> selectBlogByPage(@RequestParam(value = "page") Integer page,
                                                                  @RequestParam(value = "page_size") Integer pageSize,
                                                                  @RequestParam(value = "search_name", defaultValue = "") String searchName){
        return ApiResponse.success(BlogService.selectBlogByPage(page,pageSize,searchName));
    }

    /**
     * 添加新博客（需管理员权限）
     *
     * @param blogPost 博客内容
     * @return 操作结果
     */
    @PostMapping()
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "添加博客",
            description = "添加博客"
    )
    public ApiResponse<Void> addBlog(@RequestBody BlogPost blogPost){
        BlogService.addBlog(blogPost);
        return ApiResponse.success();
    }

    /**
     * 修改指定ID的博客（需管理员权限）
     *
     * @param blogId 博客ID
     * @param blogPost 修改后的博客内容
     * @return 操作结果
     */
    @PutMapping("/{blog_id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "修改博客",
            description = "修改博客"
    )
    public ApiResponse<Void> editBlog(@PathVariable("blog_id") Integer blogId, @RequestBody BlogPost blogPost){
        BlogService.updateBlog(blogId,blogPost);
        return ApiResponse.success();
    }

    /**
     * 删除博客（需管理员权限）
     *
     * @param blogPost 包含待删除博客信息的参数
     * @return 操作结果
     */
    @PutMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteBlog(@RequestBody Map<String,Object> blogPost){
        BlogService.deleteBlog(blogPost);
        return ApiResponse.success();
    }



}
