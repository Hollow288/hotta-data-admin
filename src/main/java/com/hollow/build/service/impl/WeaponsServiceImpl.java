package com.hollow.build.service.impl;

import com.hollow.build.dto.WeaponSensualityLevelDataDto;
import com.hollow.build.dto.WeaponSkillDto;
import com.hollow.build.dto.WeaponUpgradeStarPackDto;
import com.hollow.build.dto.WeaponsResponseDto;
import com.hollow.build.entity.WeaponSensualityLevelData;
import com.hollow.build.entity.WeaponSkill;
import com.hollow.build.entity.WeaponUpgradeStarPack;
import com.hollow.build.entity.Weapons;
import com.hollow.build.mapper.WeaponsMapper;
import com.hollow.build.service.WeaponsService;
import com.hollow.build.utils.DtoMapperUtil;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WeaponsServiceImpl implements WeaponsService {

    private final WeaponsMapper weaponsMapper;

    private final MinioUtil minioUtil;



    @Override
    @Cacheable(value = "weapons_all")
    public List<WeaponsResponseDto> getAllWeapons() {

        List<Weapons> weaponsList = weaponsMapper.findWeaponsAll();
        List<Integer> ids = weaponsList.stream().map(Weapons::getWeaponsId).toList();

        List<WeaponSkill> skills = weaponsMapper.findWeaponSkillsByWeaponsId(ids);
        List<WeaponSensualityLevelData> sensualities = weaponsMapper.findWeaponSensualitiesByWeaponsId(ids);
        List<WeaponUpgradeStarPack> stars = weaponsMapper.findWeaponUpgradeStarsByWeaponsId(ids);

        Map<Integer, WeaponsResponseDto> dtoMap = weaponsList.stream()
                .peek(w -> w.setItemIcon(minioUtil.fileUrlEncoderChance(w.getItemIcon())))
                .collect(Collectors.toMap(Weapons::getWeaponsId, w -> DtoMapperUtil.map(w, WeaponsResponseDto.class)));

        skills.forEach(skill -> {
            WeaponsResponseDto dto = dtoMap.get(skill.getWeaponsId());
            if (dto != null) {
                if (dto.getWeaponSkills() == null) {
                    dto.setWeaponSkills(new ArrayList<>());
                }
                dto.getWeaponSkills().add(DtoMapperUtil.map(skill, WeaponSkillDto.class));
            }
        });

        // 组装通感列表
        sensualities.forEach(sensuality -> {
            WeaponsResponseDto dto = dtoMap.get(sensuality.getWeaponsId());
            if (dto != null) {
                if (dto.getWeaponSensualityLevelDataList() == null) {
                    dto.setWeaponSensualityLevelDataList(new ArrayList<>());
                }
                dto.getWeaponSensualityLevelDataList().add(DtoMapperUtil.map(sensuality, WeaponSensualityLevelDataDto.class));
            }
        });

        // 组装星级列表
        stars.forEach(star -> {
            WeaponsResponseDto dto = dtoMap.get(star.getWeaponsId());
            if (dto != null) {
                if (dto.getWeaponUpgradeStarPack() == null) {
                    dto.setWeaponUpgradeStarPack(new ArrayList<>());
                }
                dto.getWeaponUpgradeStarPack().add(DtoMapperUtil.map(star, WeaponUpgradeStarPackDto.class));
            }
        });

        return new ArrayList<>(dtoMap.values());
    }

    @Override
    @Cacheable(value = "weapons", key = "#itemKey")
    public WeaponsResponseDto getWeaponByKey(String itemKey) {
        WeaponsResponseDto weaponsResponseDto = weaponsMapper.selectWeaponWithDetailsByKey(itemKey);
        if (weaponsResponseDto != null) {
            weaponsResponseDto.setItemIcon(minioUtil.fileUrlEncoderChance(weaponsResponseDto.getItemIcon()));
        }
        return weaponsResponseDto;
    }

}
