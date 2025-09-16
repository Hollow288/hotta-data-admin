package com.hollow.build.service;

import com.hollow.build.entity.mongo.Fashion;

import java.util.List;

public interface FashionService {
    List<Fashion> getAllFashion();

    Fashion getFashionByKey(String itemKey);
}
