package com.projecteden.title.dto; import jakarta.validation.constraints.NotBlank; public record SetActiveTitleRequest(@NotBlank(message="칭호 코드는 필수입니다.") String titleCode){}
