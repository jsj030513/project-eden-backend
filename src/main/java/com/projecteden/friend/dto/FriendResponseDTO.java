package com.projecteden.friend.dto;
import java.time.LocalDateTime;
import com.projecteden.friend.domain.FriendStatus;
public record FriendResponseDTO(Long friendshipId, Long friendId, String nickname, String avatarUrl, String currentSeason, boolean onlineToday, int consecutiveLogins, LocalDateTime lastLoginAt, FriendStatus status) {}
