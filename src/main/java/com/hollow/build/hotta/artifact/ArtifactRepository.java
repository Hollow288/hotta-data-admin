package com.hollow.build.hotta.artifact;

import com.hollow.build.hotta.artifact.Artifact;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 源器（Artifact）MongoDB 数据访问接口。
 */
@Repository
public interface ArtifactRepository extends MongoRepository<Artifact, String> {

    /**
     * 根据源器唯一标识键查询源器信息。
     *
     * @param artifactKey 源器唯一标识键
     * @return 匹配的源器对象，未找到时返回 null
     */
    Artifact findByArtifactKey(String artifactKey);
}
