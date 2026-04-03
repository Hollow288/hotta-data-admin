package com.hollow.build.service;

import com.hollow.build.dto.WeaponsListDto;
import com.hollow.build.entity.mongo.Weapons;

import java.util.List;

/**
 * 武器服务接口，提供武器的查询功能
 */
public interface WeaponsService {

    /**
     * 获取所有武器列表
     *
     * @return 所有武器的列表
     */
    List<Weapons> getAllWeapons();

    /**
     * 根据武器唯一标识获取武器详情
     *
     * @param weaponsKey 武器的唯一标识键
     * @return 对应的武器对象
     */
    Weapons getWeaponByKey(String weaponsKey);

    /**
     * 根据多个条件查询武器列表
     *
     * @param weaponCategory 武器类别
     * @param weaponElement 武器元素属性
     * @param weaponRarity 武器稀有度
     * @return 符合条件的武器列表DTO
     */
    List<WeaponsListDto> getWeaponsByParams(String weaponCategory, String weaponElement, String weaponRarity);
}
