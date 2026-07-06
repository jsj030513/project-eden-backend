package com.projecteden.house.dto;

import com.projecteden.house.domain.HouseType;

public record CreateHouseResponse(
		Long id,
		String houseName,
		int level,
		HouseType houseType,
		int maxDecoration) {
}
