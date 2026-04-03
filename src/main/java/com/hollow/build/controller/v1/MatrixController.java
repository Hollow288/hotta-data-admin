package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.MatrixListDto;
import com.hollow.build.entity.mongo.Matrix;
import com.hollow.build.service.MatrixService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 意志控制器，提供意志列表、详情和条件查询接口。
 */
@RestController
@RequestMapping("/api/v1/matrix")
@RequiredArgsConstructor
@Tag(name = "意志", description = "意志相关接口")
public class MatrixController {

    private final MatrixService matrixService;
    
    /**
     * 查询全部意志数据。
     *
     * @return 包含全部意志信息的响应结果
     */
    @GetMapping
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "查询所有意志", description = "获取所有意志的基本信息")
    public ApiResponse<List<Matrix>> getAllMatrix() {
        return ApiResponse.success(matrixService.getAllMatrix());
    }


    /**
     * 根据意志唯一键查询详情。
     *
     * @param itemKey 意志唯一标识
     * @return 包含意志详情的响应结果
     */
    @GetMapping("/{item_key}")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据key查询意志", description = "根据key获取意志的详细信息")
    public ApiResponse<Matrix> getMatrixByKey(@PathVariable(value = "item_key") String itemKey) {
        Matrix Matrix = matrixService.getMatrixByKey(itemKey);
        return ApiResponse.success(Matrix);
    }


    /**
     * 按品质筛选意志简要信息。
     *
     * @param matrixQuality 意志品质，可为空
     * @return 包含筛选结果列表的响应结果
     */
    @GetMapping("/search")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据条件查询意志", description = "根据条件查询意志")
    public ApiResponse<List<MatrixListDto>> getMatrixByParams(@RequestParam(required = false) String matrixQuality) {
        List<MatrixListDto> matrixListDtoList = matrixService.getMatrixByParams(matrixQuality);
        return ApiResponse.success(matrixListDtoList);
    }
}
