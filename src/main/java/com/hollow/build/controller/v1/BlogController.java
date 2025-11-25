package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.dto.PageResult;
import com.hollow.build.entity.mysql.BlogPost;
import com.hollow.build.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/blog")
@Tag(name = "博客", description = "博客")
public class BlogController {

    private final BlogService BlogService;


    @GetMapping("/blog-date-menu")
    @Operation(
            summary = "查询博客日期/数量",
            description = "查询博客日期/数量"
    )
    public ApiResponse<List<BlogDateMenuDto>> selectBlogDateMenu(){
        return ApiResponse.success(BlogService.selectBlogDateMenu());
    }


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


    @PutMapping("/{blog_id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "添加博客",
            description = "添加博客"
    )
    public ApiResponse<Void> editBlog(@PathVariable("blog_id") Integer blogId, @RequestBody BlogPost blogPost){
        BlogService.updateBlog(blogId,blogPost);
        return ApiResponse.success();
    }


    @PutMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteBlog(@RequestBody Map<String,Object> blogPost){
        BlogService.deleteBlog(blogPost);
        return ApiResponse.success();
    }



}
