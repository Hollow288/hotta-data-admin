package com.hollow.build.repository.mongo;

import com.hollow.build.entity.mongo.Weapons;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WeaponsRepository extends MongoRepository<Weapons, String> {

    // 按武器Key查询
    Weapons findByWeaponKey(String weaponKey);

}
