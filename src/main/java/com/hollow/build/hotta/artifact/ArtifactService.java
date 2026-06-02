package com.hollow.build.hotta.artifact;

import com.hollow.build.hotta.artifact.ArtifactListDto;
import com.hollow.build.hotta.artifact.Artifact;

import java.util.List;

/**
 * 源器服务接口，提供源器的查询功能
 */
public interface ArtifactService {

    /**
     * 获取所有源器列表
     *
     * @return 所有源器的列表
     */
    List<Artifact> getAllArtifact();

    /**
     * 根据源器唯一标识获取源器详情
     *
     * @param itemKey 源器的唯一标识键
     * @return 对应的源器对象
     */
    Artifact getArtifactByKey(String itemKey);

    /**
     * 根据稀有度查询源器列表
     *
     * @param artifactRarity 源器稀有度
     * @return 符合条件的源器列表DTO
     */
    List<ArtifactListDto> getArtifactByParams(String artifactRarity);
}
