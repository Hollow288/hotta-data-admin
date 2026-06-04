package com.hollow.build.hotta.weapons;

import com.hollow.build.common.ApiResponse;
import com.hollow.build.ratelimit.BypassRateLimit;
import com.hollow.build.auth.config.PublicEndpoint;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 武器控制器，提供武器列表、详情和条件筛选接口。
 */
@RestController
@RequestMapping("/api/v1/weapons")
@Tag(name = "武器", description = "武器相关接口")
public class WeaponsController {

    private final WeaponsService weaponsService;

    /**
     * 创建武器控制器实例。
     *
     * @param weaponsService 武器服务
     */
    public WeaponsController(WeaponsService weaponsService) {
        this.weaponsService = weaponsService;
    }

    /**
     * 查询全部武器数据。
     *
     * @return 包含全部武器信息的响应结果
     */
    @GetMapping
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "查询所有武器", description = "获取所有武器的基本信息")
    public ApiResponse<List<Weapons>> getAllWeapons() {
        return ApiResponse.success(weaponsService.getAllWeapons());
    }


    /**
     * 根据武器唯一键查询详情。
     *
     * @param itemKey 武器唯一标识
     * @return 包含武器详情的响应结果
     */
    @GetMapping("/{item_key}")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据key查询武器", description = "根据key获取武器的详细信息")
    public ApiResponse<Weapons> getWeaponByKey(@PathVariable(value = "item_key") String itemKey) {
        Weapons weapons = weaponsService.getWeaponByKey(itemKey);
        return ApiResponse.success(weapons);
    }

    /**
     * 按分类、属性和稀有度筛选武器列表。
     *
     * @param weaponCategory 武器分类，可为空
     * @param weaponElement 武器元素类型，可为空
     * @param weaponRarity 武器稀有度，可为空
     * @return 包含武器简要信息列表的响应结果
     */
    @GetMapping("/search")
    @BypassRateLimit
    @PublicEndpoint
    @Operation(summary = "根据条件查询武器", description = "根据条件查询武器")
    public ApiResponse<List<WeaponsListDto>> getWeaponsByParams(@RequestParam(required = false) String weaponCategory, @RequestParam(required = false) String weaponElement, @RequestParam(required = false) String weaponRarity) {
        List<WeaponsListDto> weaponsListDtoList = weaponsService.getWeaponsByParams(weaponCategory,weaponElement,weaponRarity);
        return ApiResponse.success(weaponsListDtoList);
    }
}
