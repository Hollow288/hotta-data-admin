package com.hollow.build.hotta.fashion;

import com.hollow.build.hotta.fashion.Fashion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 时装（Fashion）MongoDB 数据访问接口。
 */
@Repository
public interface FashionRepository extends MongoRepository<Fashion, String> {

    /**
     * 根据时装唯一标识键查询时装信息。
     *
     * @param artifactKey 时装唯一标识键
     * @return 匹配的时装对象，未找到时返回 null
     */
    Fashion findByFashionKey(String artifactKey);
}
