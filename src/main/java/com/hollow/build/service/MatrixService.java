package com.hollow.build.service;

import com.hollow.build.dto.MatrixListDto;
import com.hollow.build.entity.mongo.Matrix;

import java.util.List;

public interface MatrixService {
    List<Matrix> getAllMatrix();

    Matrix getMatrixByKey(String itemKey);

    List<MatrixListDto> getMatrixByParams(String matrixQuality);
}
