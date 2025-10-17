package com.hollow.build.service.impl;

import com.hollow.build.entity.mongo.Artifact;
import com.hollow.build.repository.mongo.ArtifactRepository;
import com.hollow.build.service.ArtifactService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArtifactServiceImpl implements ArtifactService {

    private final ArtifactRepository artifactRepository;

    private final MinioUtil minioUtil;
    
    @Override
    @Cacheable(value = "artifact_all")
    public List<Artifact> getAllArtifact() {
        return artifactRepository.findAll().stream()
                .peek(artifact -> {
                    artifact.setArtifactIcon(minioUtil.fileUrlEncoderChance(artifact.getArtifactIcon(),"hotta"));
                    artifact.setArtifactThumbnail(minioUtil.fileUrlEncoderChance(artifact.getArtifactThumbnail(),"hotta"));
                }).toList();
    }

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
}
