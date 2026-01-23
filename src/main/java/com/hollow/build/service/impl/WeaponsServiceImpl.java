package com.hollow.build.service.impl;


import com.hollow.build.dto.WeaponsListDto;
import com.hollow.build.entity.mongo.Weapons;
import com.hollow.build.repository.mongo.WeaponsRepository;
import com.hollow.build.service.WeaponsService;


import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class WeaponsServiceImpl implements WeaponsService {

    private final WeaponsRepository weaponsRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;

    @Override
    @Cacheable(value = "weapons_all")
    public List<Weapons> getAllWeapons() {
        return weaponsRepository.findAll().stream()
                .peek(weapons -> {

                    // 武器图标
                    weapons.setWeaponIcon(
                            minioUtil.fileUrlEncoderChance(weapons.getWeaponIcon(), "hotta")
                    );

                    // 技能图标
                    if (weapons.getWeaponSkill() != null) {
                        weapons.getWeaponSkill().forEach(skill ->
                                skill.setIcon(
                                        minioUtil.fileUrlEncoderChance(skill.getIcon(), "hotta")
                                )
                        );
                    }

                    // 基础属性图标 attributeIcon
                    if (weapons.getWeaponModifyData() != null) {
                        weapons.getWeaponModifyData().forEach(modifyData ->
                                modifyData.setAttributeIcon(
                                        minioUtil.fileUrlEncoderChance(modifyData.getAttributeIcon(), "hotta")
                                )
                        );
                    }

                })
                .toList();
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

        // 基础属性图标 attributeIcon
        if (weapons.getWeaponModifyData() != null) {
            weapons.getWeaponModifyData().forEach(modifyData ->
                    modifyData.setAttributeIcon(
                            minioUtil.fileUrlEncoderChance(modifyData.getAttributeIcon(), "hotta")
                    )
            );
        }

        return weapons;
    }

    @Override
    public List<WeaponsListDto> getWeaponsByParams(String weaponCategory, String weaponElement, String weaponRarity) {

        // 创建一个列表，用来放匹配条件
        List<Criteria> criteriaList = new ArrayList<>();

        if (weaponCategory != null && !weaponCategory.isEmpty()) {
            criteriaList.add(Criteria.where("weaponCategory").is(weaponCategory));
        }

        if (weaponRarity != null && !weaponRarity.isEmpty()) {
            criteriaList.add(Criteria.where("weaponRarity").is(weaponRarity));
        }

        if (weaponElement != null && !weaponElement.isEmpty()) {
            criteriaList.add(Criteria.where("weaponElement.weaponElementType").is(weaponElement));
        }

        // 创建聚合操作列表
        List<AggregationOperation> operations = new ArrayList<>();

        // 如果有匹配条件，就加上 match 操作
        if (!criteriaList.isEmpty()) {
            operations.add(Aggregation.match(new Criteria().andOperator(criteriaList.toArray(new Criteria[0]))));
        }

        // 投影操作：指定要返回的字段，同时把嵌套字段扁平化到 DTO 顶层
        operations.add(
                Aggregation.project("weaponKey", "weaponName", "weaponIcon", "weaponRarity", "weaponCategory")
                        .and("weaponElement.weaponElementType").as("weaponElementType")
        );

        // 执行聚合查询
        Aggregation aggregation = Aggregation.newAggregation(operations);
        List<WeaponsListDto> weaponSearchList = mongoTemplate.aggregate(aggregation, "weapons", WeaponsListDto.class)
                .getMappedResults();

        // 处理图标 URL
        weaponSearchList.forEach(weaponListDto -> {
            weaponListDto.setWeaponIcon(minioUtil.fileUrlEncoderChance(weaponListDto.getWeaponIcon(), "hotta"));
        });

        return weaponSearchList;
    }
}
