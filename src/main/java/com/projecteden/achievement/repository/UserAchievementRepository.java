package com.projecteden.achievement.repository;
import java.util.List; import org.springframework.data.jpa.repository.JpaRepository; import com.projecteden.achievement.domain.UserAchievement;
public interface UserAchievementRepository extends JpaRepository<UserAchievement,Long>{boolean existsByCharacterIdAndAchievementCode(Long characterId,String achievementCode);long countByCharacterIdAndAchievementCode(Long characterId,String achievementCode);List<UserAchievement> findByCharacterId(Long characterId);long countByCharacterId(Long characterId);}
