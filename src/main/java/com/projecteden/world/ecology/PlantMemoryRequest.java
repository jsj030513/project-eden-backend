package com.projecteden.world.ecology;

import jakarta.validation.constraints.NotNull;

public record PlantMemoryRequest(
        @NotNull(message = "사진 ID는 필수입니다.") Long photoId,
        @NotNull(message = "심기 대상 ID는 필수입니다.") Long targetId,
        @NotNull(message = "심기 대상 X 좌표는 필수입니다.") Integer expectedX,
        @NotNull(message = "심기 대상 Y 좌표는 필수입니다.") Integer expectedY) {
}
