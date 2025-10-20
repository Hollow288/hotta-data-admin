package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.ArtifactListDto;
import com.hollow.build.entity.mongo.Artifact;
import com.hollow.build.service.ArtifactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/artifact")
@RequiredArgsConstructor
@Tag(name = "源器", description = "源器相关接口")
public class ArtifactController {

    private final ArtifactService artifactService;
    
    @GetMapping
    @PublicEndpoint
    @Operation(summary = "查询所有源器", description = "获取所有源器的基本信息")
    public ApiResponse<List<Artifact>> getAllArtifact() {
        return ApiResponse.success(artifactService.getAllArtifact());
    }


    @GetMapping("/{item_key}")
    @PublicEndpoint
    @Operation(summary = "根据key查询源器", description = "根据key获取源器的详细信息")
    public ApiResponse<Artifact> getArtifactByKey(@PathVariable(value = "item_key") String itemKey) {
        Artifact artifact = artifactService.getArtifactByKey(itemKey);
        return ApiResponse.success(artifact);
    }


    @GetMapping("/search")
    @PublicEndpoint
    @Operation(summary = "根据条件查询源器", description = "根据条件查询源器")
    public ApiResponse<List<ArtifactListDto>> getArtifactByParams(@RequestParam(required = false) String artifactRarity) {
        List<ArtifactListDto> artifactListDtoList = artifactService.getArtifactByParams(artifactRarity);
        return ApiResponse.success(artifactListDtoList);
    }
}
