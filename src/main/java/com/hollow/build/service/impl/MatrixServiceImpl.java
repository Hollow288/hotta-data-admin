package com.hollow.build.service.impl;

import com.hollow.build.dto.MatrixListDto;
import com.hollow.build.entity.mongo.Matrix;
import com.hollow.build.repository.mongo.MatrixRepository;
import com.hollow.build.service.MatrixService;
import com.hollow.build.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 意志服务实现类，负责查询意志数据并补全相关图标访问地址。
 */
@Service
@RequiredArgsConstructor
public class MatrixServiceImpl implements MatrixService {

    private final MatrixRepository matrixRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;
    
    /**
     * 查询全部意志信息，并补全主图、套装图标和属性图标地址。
     *
     * @return 全部意志列表
     */
    @Override
    @Cacheable(value = "matrix_all")
    public List<Matrix> getAllMatrix() {
        List<Matrix> list = matrixRepository.findAll();

        list.forEach(matrix -> {
            matrix.setMatrixIcon(
                    minioUtil.fileUrlEncoderChance(matrix.getMatrixIcon(), "hotta")
            );

            matrix.getMatrixSuitList().forEach(suit -> {
                suit.setTypeIcon(
                        minioUtil.fileUrlEncoderChance(suit.getTypeIcon(), "hotta")
                );

                suit.getMatrixModifyData().forEach(modify -> {
                    modify.setAttributeIcon(
                            minioUtil.fileUrlEncoderChance(modify.getAttributeIcon(), "hotta")
                    );
                });
            });
        });

        return list;
    }

    /**
     * 根据意志唯一键查询详情，并补全关联图片访问地址。
     *
     * @param itemKey 意志唯一标识
     * @return 意志详情，未找到时返回 null
     */
    @Override
    @Cacheable(value = "matrix", key = "#itemKey")
    public Matrix getMatrixByKey(String itemKey) {
        Matrix matrix = matrixRepository.findByMatrixKey(itemKey);

        if (matrix == null) {
            return null;
        }

        // 拼接主图标
        matrix.setMatrixIcon(minioUtil.fileUrlEncoderChance(matrix.getMatrixIcon(),"hotta"));

        matrix.getMatrixSuitList().forEach(suit -> {
            suit.setTypeIcon(
                    minioUtil.fileUrlEncoderChance(suit.getTypeIcon(), "hotta")
            );

            suit.getMatrixModifyData().forEach(modify -> {
                modify.setAttributeIcon(
                        minioUtil.fileUrlEncoderChance(modify.getAttributeIcon(), "hotta")
                );
            });
        });
        return matrix;
    }

    /**
     * 按品质筛选意志简要信息。
     *
     * @param matrixQuality 意志品质，可为空
     * @return 满足条件的意志列表 DTO 集合
     */
    @Override
    public List<MatrixListDto> getMatrixByParams(String matrixQuality) {
        Query query = new Query();


        if (matrixQuality != null && !matrixQuality.isEmpty()) {
            query.addCriteria(Criteria.where("matrixQuality").is(matrixQuality));
        }

        query.fields()
                .include("matrixKey")
                .include("matrixName")
                .include("matrixQuality")
                .include("matrixThumbnail");

        List<MatrixListDto> matrixSearchList = mongoTemplate.find(query, MatrixListDto.class, "matrix");

        matrixSearchList.forEach(matrixListDto -> {
            matrixListDto.setMatrixThumbnail(minioUtil.fileUrlEncoderChance(matrixListDto.getMatrixThumbnail(),"hotta"));
        });
        return matrixSearchList;
    }
}
