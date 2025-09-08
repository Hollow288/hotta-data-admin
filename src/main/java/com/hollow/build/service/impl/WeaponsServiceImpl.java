package com.hollow.build.service.impl;


import com.hollow.build.entity.mongo.Weapons;
import com.hollow.build.repository.mongo.WeaponsRepository;
import com.hollow.build.service.WeaponsService;


import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


import java.util.List;


@Service
@RequiredArgsConstructor
public class WeaponsServiceImpl implements WeaponsService {

    private final WeaponsRepository weaponsRepository;

    private final MinioUtil minioUtil;

    @Override
    @Cacheable(value = "weapons_all")
    public List<Weapons> getAllWeapons() {
        return weaponsRepository.findAll().stream()
                .peek(weapons -> {
                    weapons.setWeaponIcon(minioUtil.fileUrlEncoderChance(weapons.getWeaponIcon(),"hotta"));
                    weapons.getWeaponSkill().forEach(skill ->
                            skill.setIcon(minioUtil.fileUrlEncoderChance(skill.getIcon(),"hotta"))
                    );
                }).toList();
    }

    @Override
    @Cacheable(value = "weapons", key = "#itemKey")
    public Weapons getWeaponByKey(String itemKey) {
        Weapons weapons = weaponsRepository.findByWeaponKey(itemKey);

        if (weapons == null) {
            return null;
        }

        // 拼接主图标
        weapons.setWeaponIcon(minioUtil.fileUrlEncoderChance(weapons.getWeaponIcon(),"hotta"));

        // 拼接技能图标
        if (weapons.getWeaponSkill() != null) {
            weapons.getWeaponSkill().forEach(skill ->
                    skill.setIcon(minioUtil.fileUrlEncoderChance(skill.getIcon(),"hotta"))
            );
        }

        return weapons;
    }

}
