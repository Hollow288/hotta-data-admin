package com.hollow.build.service;

import com.hollow.build.dto.WeaponsResponseDto;

import java.util.List;

public interface WeaponsService {

    List<WeaponsResponseDto> getAllWeapons();

    WeaponsResponseDto getWeaponByKey(String weaponsKey);
}
