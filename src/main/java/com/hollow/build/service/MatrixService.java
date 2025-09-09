package com.hollow.build.service;

import com.hollow.build.entity.mongo.Matrix;

import java.util.List;

public interface MatrixService {
    List<Matrix> getAllMatrix();

    Matrix getMatrixByKey(String itemKey);
}
