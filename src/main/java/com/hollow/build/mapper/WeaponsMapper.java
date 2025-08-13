package com.hollow.build.mapper;

import com.hollow.build.dto.WeaponsResponseDto;
import com.hollow.build.entity.WeaponSensualityLevelData;
import com.hollow.build.entity.WeaponSkill;
import com.hollow.build.entity.WeaponUpgradeStarPack;
import com.hollow.build.entity.Weapons;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WeaponsMapper {

    WeaponsResponseDto selectWeaponWithDetailsByKey(String itemKey);

    List<Weapons> findWeaponsAll();

    List<WeaponSkill> findWeaponSkillsByWeaponsId(List<Integer> id);

    List<WeaponSensualityLevelData> findWeaponSensualitiesByWeaponsId(List<Integer> id);

    List<WeaponUpgradeStarPack> findWeaponUpgradeStarsByWeaponsId(List<Integer> id);

}
