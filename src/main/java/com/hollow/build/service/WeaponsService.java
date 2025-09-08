package com.hollow.build.service;

import com.hollow.build.entity.mongo.Weapons;

import java.util.List;

public interface WeaponsService {

    List<Weapons> getAllWeapons();

    Weapons getWeaponByKey(String weaponsKey);
}
