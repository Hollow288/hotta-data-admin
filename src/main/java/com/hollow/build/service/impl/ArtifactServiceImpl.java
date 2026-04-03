package com.hollow.build.service.impl;

import com.hollow.build.dto.ArtifactListDto;
import com.hollow.build.entity.mongo.Artifact;
import com.hollow.build.repository.mongo.ArtifactRepository;
import com.hollow.build.service.ArtifactService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 源器（Artifact）服务实现类，提供源器的查询功能，包括全量查询、按 Key 查询和条件筛选。
 */
@Service
@RequiredArgsConstructor
public class ArtifactServiceImpl implements ArtifactService {

    private final ArtifactRepository artifactRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;
    
    /**
     * 获取所有源器列表，并对图标和缩略图 URL 进行编码处理。
     * 查询结果会被缓存。
     *
     * @return 所有源器的列表
     */
    @Override
    @Cacheable(value = "artifact_all")
    public List<Artifact> getAllArtifact() {
        return artifactRepository.findAll().stream()
                .peek(artifact -> {
                    artifact.setArtifactIcon(minioUtil.fileUrlEncoderChance(artifact.getArtifactIcon(),"hotta"));
                    artifact.setArtifactThumbnail(minioUtil.fileUrlEncoderChance(artifact.getArtifactThumbnail(),"hotta"));
                }).toList();
    }

    /**
     * 根据源器的唯一标识 Key 查询源器详情，并处理图标 URL。
     * 查询结果会被缓存。
     *
     * @param itemKey 源器的唯一标识
     * @return 源器详情，若未找到则返回 null
     */
    @Override
    @Cacheable(value = "artifact", key = "#itemKey")
    public Artifact getArtifactByKey(String itemKey) {
        Artifact artifact = artifactRepository.findByArtifactKey(itemKey);

        if (artifact == null) {
            return null;
        }

        // 拼接主图标
        artifact.setArtifactIcon(minioUtil.fileUrlEncoderChance(artifact.getArtifactIcon(),"hotta"));
        artifact.setArtifactThumbnail(minioUtil.fileUrlEncoderChance(artifact.getArtifactThumbnail(),"hotta"));

        return artifact;
    }

    /**
     * 根据稀有度条件筛选源器列表，返回简要信息。
     *
     * @param artifactRarity 源器稀有度，可为 null 表示不筛选
     * @return 满足条件的源器简要信息列表
     */
    @Override
    public List<ArtifactListDto> getArtifactByParams(String artifactRarity) {

        Query query = new Query();

        if (artifactRarity != null && !artifactRarity.isEmpty()) {
            query.addCriteria(Criteria.where("artifactRarity").is(artifactRarity));
        }

        query.fields()
                .include("artifactKey")
                .include("artifactName")
                .include("artifactRarity")
                .include("artifactThumbnail");

        List<ArtifactListDto> artifactSearchList = mongoTemplate.find(query, ArtifactListDto.class, "artifact");

        artifactSearchList.forEach(artifactListDto -> {
            artifactListDto.setArtifactThumbnail(minioUtil.fileUrlEncoderChance(artifactListDto.getArtifactThumbnail(),"hotta"));
        });
        return artifactSearchList;
    }
}
