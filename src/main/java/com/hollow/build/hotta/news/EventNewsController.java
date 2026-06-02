package com.hollow.build.hotta.news;


import com.hollow.build.common.ApiResponse;
import com.hollow.build.auth.config.PublicEndpoint;
import com.hollow.build.hotta.news.EventNewsDto;
import com.hollow.build.common.PageResult;
import com.hollow.build.hotta.news.EventNewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * 活动资讯控制器，提供活动的增删改查及分页查询功能
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/event-news")
@Tag(name = "活动", description = "活动")
public class EventNewsController {

    private final EventNewsService eventNewsService;

    /**
     * 分页查询活动列表（需管理员权限）
     *
     * @param page 页码，从 1 开始
     * @param pageSize 每页数量
     * @param searchName 搜索名称（可选）
     * @return 分页活动列表
     */
    @GetMapping()
//    @PublicEndpoint
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "分页查询活动列表",
            description = "根据页码、每页数量以及搜索名称分页查询活动"
    )
    public ApiResponse<PageResult<EventNewsDto>> eventNewsByPage(
            @Parameter(description = "页码，从 1 开始", required = true, example = "1")
            @RequestParam(value = "page") Integer page,

            @Parameter(description = "每页数量", required = true, example = "10")
            @RequestParam(value = "page_size") Integer pageSize,

            @Parameter(description = "搜索名称，可以为空", required = false, example = "订购")
            @RequestParam(value = "search_name", defaultValue = "") String searchName){
        return ApiResponse.success(eventNewsService.eventNewsByPage(page,pageSize,searchName));
    }

    /**
     * 根据ID查询活动详情（需管理员权限）
     *
     * @param newsId 活动ID
     * @return 活动详细信息
     */
    @GetMapping("/{news_id}")
//    @PublicEndpoint
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "ID查询活动",
            description = "根据ID查询活动"
    )
    public ApiResponse<EventNewsDto> eventNewsById(@PathVariable("news_id") Integer newsId){
        return ApiResponse.success(eventNewsService.eventNewsById(newsId));
    }

    /**
     * 添加新活动（需管理员权限）
     *
     * @param eventNewsDto 活动信息
     * @return 操作结果
     */
    @PostMapping()
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "添加活动",
            description = "添加活动"
    )
    public ApiResponse<Void> addEventNews(@RequestBody EventNewsDto eventNewsDto){
        eventNewsService.addEventNews(eventNewsDto);
        return ApiResponse.success();
    }

    /**
     * 修改指定ID的活动（需管理员权限）
     *
     * @param newsId 活动ID
     * @param eventNewsDto 修改后的活动信息
     * @return 操作结果
     */
    @PutMapping("/{news_id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "修改活动",
            description = "修改活动"
    )
    public ApiResponse<Void> updateEventNews(@PathVariable("news_id") Integer newsId, @RequestBody EventNewsDto eventNewsDto){
        eventNewsService.updateEventNews(newsId,eventNewsDto);
        return ApiResponse.success();
    }

    /**
     * 删除活动（需管理员权限）
     *
     * @param eventEventNews 包含待删除活动信息的参数
     * @return 操作结果
     */
    @PutMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteEventNews(@RequestBody Map<String,Object> eventEventNews){
        eventNewsService.deleteEventNews(eventEventNews);
        return ApiResponse.success();
    }




}
