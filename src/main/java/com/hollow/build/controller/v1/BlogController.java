package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.BlogDateListDto;
import com.hollow.build.dto.BlogDateMenuDto;
import com.hollow.build.entity.mysql.BlogPost;
import com.hollow.build.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


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
    //    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<BlogDateMenuDto>> selectBlogDateMenu(){
        return ApiResponse.success(BlogService.selectBlogDateMenu());
    }


    @GetMapping("/blog-date-list/{date}")
    @Operation(
            summary = "根据日期查询博客列表",
            description = "根据日期查询博客列表"
    )
    //    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<List<BlogDateListDto>> selectBlogDateListByDate(@PathVariable("date") String date){
        return ApiResponse.success(BlogService.selectBlogDateListByDate(date));
    }


    @GetMapping("/{articleId}")
    @Operation(
            summary = "根据ID查询博客",
            description = "根据ID查询博客"
    )
    //    @BypassRateLimit
    @PublicEndpoint
    public ApiResponse<BlogPost> selectBlogById(@PathVariable("articleId") String articleId){
        return ApiResponse.success(BlogService.selectBlogById(articleId));
    }


}
