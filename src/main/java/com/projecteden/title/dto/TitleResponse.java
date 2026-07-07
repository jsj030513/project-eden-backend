package com.projecteden.title.dto; import java.time.LocalDateTime; public record TitleResponse(String code,String name,String description,boolean acquired,boolean active,LocalDateTime acquiredAt){}
