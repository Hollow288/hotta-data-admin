package com.hollow.build.repository.mongo;

import com.hollow.build.entity.mongo.Artifact;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtifactRepository extends MongoRepository<Artifact, String> {
    // 按武器Key查询
    Artifact findByArtifactKey(String artifactKey);
}
