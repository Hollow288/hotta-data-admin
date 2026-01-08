package com.hollow.build.controller.v1;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.config.BypassRateLimit;
import com.hollow.build.config.PublicEndpoint;
import com.hollow.build.dto.WeaponsListDto;
import com.hollow.build.entity.mongo.Weapons;
import com.hollow.build.service.WeaponsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "查询所有武器", description = "获取所有武器的基本信息")
    public ApiResponse<List<Weapons>> getAllWeapons() {
        return ApiResponse.success(weaponsService.getAllWeapons());
    }


    @GetMapping("/{item_key}")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据key查询武器", description = "根据key获取武器的详细信息")
    public ApiResponse<Weapons> getWeaponByKey(@PathVariable(value = "item_key") String itemKey) {
        Weapons weapons = weaponsService.getWeaponByKey(itemKey);
        return ApiResponse.success(weapons);
    }

    @GetMapping("/search")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据条件查询武器", description = "根据条件查询武器")
    public ApiResponse<List<WeaponsListDto>> getWeaponsByParams(@RequestParam(required = false) String weaponCategory, @RequestParam(required = false) String weaponElement, @RequestParam(required = false) String weaponRarity) {
        List<WeaponsListDto> weaponsListDtoList = weaponsService.getWeaponsByParams(weaponCategory,weaponElement,weaponRarity);
        return ApiResponse.success(weaponsListDtoList);
    }
}
