package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.dto.WeaponsResponseDto;
import com.hollow.build.service.WeaponsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/weapons")
@Tag(name = "武器", description = "武器相关接口")
public class WeaponsController {

    private final WeaponsService weaponsService;

    public WeaponsController(WeaponsService weaponsService) {
        this.weaponsService = weaponsService;
    }

    @GetMapping
//    @PreAuthorize("hasRole('ADMIN')")
//    @PublicEndpoint
    @BypassRateLimit
    @Operation(summary = "查询所有武器", description = "获取所有武器的基本信息")
    public ApiResponse<List<WeaponsResponseDto>> getAllWeapons() {
        return ApiResponse.success(weaponsService.getAllWeapons());
    }


    @GetMapping("/{item_key}")
    @BypassRateLimit
    @Operation(summary = "根据key查询武器", description = "根据key获取武器的详细信息")
    public ApiResponse<WeaponsResponseDto> getWeaponByKey(@PathVariable(value = "item_key") String itemKey) {
        WeaponsResponseDto dto = weaponsService.getWeaponByKey(itemKey);
        return ApiResponse.success(dto);
    }
}
