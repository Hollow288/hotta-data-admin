package com.hollow.build.service.impl;

import com.hollow.build.entity.mongo.Fashion;
import com.hollow.build.repository.mongo.FashionRepository;
import com.hollow.build.service.FashionService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 时装服务实现类，负责查询时装数据并补全图标访问地址。
 */
@Service
@RequiredArgsConstructor
public class FashionServiceImpl implements FashionService {

    private final FashionRepository fashionRepository;

    private final MinioUtil minioUtil;
    
    /**
     * 查询全部时装信息，并补全图标访问地址。
     *
     * @return 全部时装列表
     */
    @Override
    @Cacheable(value = "fashion_all")
    public List<Fashion> getAllFashion() {
        return fashionRepository.findAll().stream()
                .peek(fashion -> {
                    List<String> encodedIcons = fashion.getFashionIcons().stream()
                            .map(icon -> minioUtil.fileUrlEncoderChance(icon, "hotta"))
                            .toList();
                    fashion.setFashionIcons(encodedIcons);
                })
                .toList();
    }

    /**
     * 根据时装唯一键查询详情，并补全图标访问地址。
     *
     * @param itemKey 时装唯一标识
     * @return 时装详情，未找到时返回 null
     */
    @Override
    @Cacheable(value = "fashion", key = "#itemKey")
    public Fashion getFashionByKey(String itemKey) {
        Fashion fashion = fashionRepository.findByFashionKey(itemKey);

        if (fashion == null) {
            return null;
        }

        // 拼接主图标
        List<String> encodedIcons = fashion.getFashionIcons().stream()
                .map(icon -> minioUtil.fileUrlEncoderChance(icon, "hotta"))
                .toList();
        fashion.setFashionIcons(encodedIcons);

        return fashion;
    }
}
