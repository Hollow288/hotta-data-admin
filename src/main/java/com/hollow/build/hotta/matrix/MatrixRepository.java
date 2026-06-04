package com.hollow.build.hotta.matrix;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * 芯片（Matrix）MongoDB 数据访问接口。
 */
@Repository
public interface MatrixRepository extends MongoRepository<Matrix, String> {

    /**
     * 根据芯片唯一标识键查询芯片信息。
     *
     * @param matrixKey 芯片唯一标识键
     * @return 匹配的芯片对象，未找到时返回 null
     */
    Matrix findByMatrixKey(String matrixKey);
}
