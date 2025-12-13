package com.hollow.build.service;

import com.hollow.build.dto.ArtifactListDto;
import com.hollow.build.entity.mongo.Artifact;

import java.util.List;

public interface ArtifactService {
    List<Artifact> getAllArtifact();

    Artifact getArtifactByKey(String itemKey);

    List<ArtifactListDto> getArtifactByParams(String artifactRarity);
}
