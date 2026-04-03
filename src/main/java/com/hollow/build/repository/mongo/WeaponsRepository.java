package com.hollow.build.repository.mongo;

import com.hollow.build.entity.mongo.Weapons;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 武器（Weapons）MongoDB 数据访问接口。
 */
@Repository
public interface WeaponsRepository extends MongoRepository<Weapons, String> {

    /**
     * 根据武器唯一标识键查询武器信息。
     *
     * @param weaponKey 武器唯一标识键
     * @return 匹配的武器对象，未找到时返回 null
     */
    Weapons findByWeaponKey(String weaponKey);

}
