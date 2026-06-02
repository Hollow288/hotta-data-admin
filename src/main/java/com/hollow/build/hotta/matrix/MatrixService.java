package com.hollow.build.hotta.matrix;

import com.hollow.build.hotta.matrix.MatrixListDto;
import com.hollow.build.hotta.matrix.Matrix;

import java.util.List;

/**
 * 芯片服务接口，提供芯片（矩阵）的查询功能
 */
public interface MatrixService {

    /**
     * 获取所有芯片列表
     *
     * @return 所有芯片的列表
     */
    List<Matrix> getAllMatrix();

    /**
     * 根据芯片唯一标识获取芯片详情
     *
     * @param itemKey 芯片的唯一标识键
     * @return 对应的芯片对象
     */
    Matrix getMatrixByKey(String itemKey);

    /**
     * 根据品质查询芯片列表
     *
     * @param matrixQuality 芯片品质
     * @return 符合条件的芯片列表DTO
     */
    List<MatrixListDto> getMatrixByParams(String matrixQuality);
}
