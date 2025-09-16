package com.hollow.build.repository.mongo;

import com.hollow.build.entity.mongo.Fashion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FashionRepository extends MongoRepository<Fashion, String> {
    // 按武器Key查询
    Fashion findByFashionKey(String artifactKey);
}
