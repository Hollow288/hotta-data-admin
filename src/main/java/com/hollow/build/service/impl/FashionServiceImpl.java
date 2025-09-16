package com.hollow.build.service.impl;

import com.hollow.build.entity.mongo.Fashion;
import com.hollow.build.repository.mongo.FashionRepository;
import com.hollow.build.service.FashionService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FashionServiceImpl implements FashionService {

    private final FashionRepository fashionRepository;

    private final MinioUtil minioUtil;
    
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
