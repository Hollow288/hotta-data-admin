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


/**
 * 武器服务实现类，负责查询武器数据并补全图标与筛选结果。
 */
@Service
@RequiredArgsConstructor
public class WeaponsServiceImpl implements WeaponsService {

    private final WeaponsRepository weaponsRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;

    /**
     * 查询全部武器信息，并补全图标访问地址。
     *
     * @return 全部武器列表
     */
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


    /**
     * 根据武器唯一键查询详情，并补全图标访问地址。
     *
     * @param itemKey 武器唯一标识
     * @return 武器详情，未找到时返回 null
     */
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

    /**
     * 按分类、元素和稀有度筛选武器简要信息。
     *
     * @param weaponCategory 武器分类，可为空
     * @param weaponElement 武器元素类型，可为空
     * @param weaponRarity 武器稀有度，可为空
     * @return 满足条件的武器列表 DTO 集合
     */
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
