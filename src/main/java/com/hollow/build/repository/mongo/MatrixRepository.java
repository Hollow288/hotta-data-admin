package com.hollow.build.repository.mongo;

import com.hollow.build.entity.mongo.Matrix;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatrixRepository extends MongoRepository<Matrix, String> {
    // 按武器Key查询
    Matrix findByMatrixKey(String matrixKey);
}
