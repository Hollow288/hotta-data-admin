package com.hollow.build.controller.v1;


import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.EventNewsDto;
import com.hollow.build.dto.PageResult;
import com.hollow.build.service.EventNewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/event-news")
@Tag(name = "活动", description = "活动")
public class EventNewsController {

    private final EventNewsService eventNewsService;

    @GetMapping()
    @PublicEndpoint
//    @PreAuthorize("hasRole('ADMIN')")
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

    @GetMapping("/{news_id}")
    @PublicEndpoint
//    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "ID查询活动",
            description = "根据ID查询活动"
    )
    public ApiResponse<EventNewsDto> eventNewsById(@PathVariable("news_id") Integer newsId){
        return ApiResponse.success(eventNewsService.eventNewsById(newsId));
    }

    @PostMapping()
    @PublicEndpoint
    @Operation(
            summary = "添加活动",
            description = "添加活动"
    )
    public ApiResponse<Void> addEventNews(@RequestBody EventNewsDto eventNewsDto){
        eventNewsService.addEventNews(eventNewsDto);
        return ApiResponse.success();
    }

    @PutMapping("/{news_id}")
    @PublicEndpoint
    @Operation(
            summary = "修改活动",
            description = "修改活动"
    )
    public ApiResponse<Void> updateEventNews(@PathVariable("news_id") Integer newsId, @RequestBody EventNewsDto eventNewsDto){
        eventNewsService.updateEventNews(newsId,eventNewsDto);
        return ApiResponse.success();
    }


    @PutMapping("/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteEventNews(@RequestBody Map<String,Object> eventEventNews){
        eventNewsService.deleteEventNews(eventEventNews);
        return ApiResponse.success();
    }




}
