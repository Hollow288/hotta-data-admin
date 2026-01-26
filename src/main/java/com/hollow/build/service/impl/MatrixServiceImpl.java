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

@Service
@RequiredArgsConstructor
public class MatrixServiceImpl implements MatrixService {

    private final MatrixRepository matrixRepository;

    private final MinioUtil minioUtil;

    private final MongoTemplate mongoTemplate;
    
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
