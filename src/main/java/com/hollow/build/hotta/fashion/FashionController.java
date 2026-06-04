package com.hollow.build.hotta.fashion;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.ratelimit.BypassRateLimit;
import com.hollow.build.auth.config.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 时装控制器，提供时装的查询相关接口
 */
@RestController
@RequestMapping("/api/v1/fashion")
@RequiredArgsConstructor
@Tag(name = "时装", description = "时装相关接口")
public class FashionController {

    private final FashionService fashionService;

    /**
     * 查询所有时装信息
     *
     * @return 所有时装列表
     */
    @GetMapping
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "查询所有时装", description = "获取所有时装的基本信息")
    public ApiResponse<List<Fashion>> getAllFashion() {
        return ApiResponse.success(fashionService.getAllFashion());
    }

    /**
     * 根据唯一标识查询时装详细信息
     *
     * @param itemKey 时装的唯一标识
     * @return 时装详细信息
     */
    @GetMapping("/{item_key}")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据key查询时装", description = "根据key获取时装的详细信息")
    public ApiResponse<Fashion> getFashionByKey(@PathVariable(value = "item_key") String itemKey) {
        Fashion Fashion = fashionService.getFashionByKey(itemKey);
        return ApiResponse.success(Fashion);
    }
}
