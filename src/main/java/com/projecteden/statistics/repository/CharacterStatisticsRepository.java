package com.projecteden.statistics.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.projecteden.statistics.domain.CharacterStatistics;
public interface CharacterStatisticsRepository extends JpaRepository<CharacterStatistics,Long>{Optional<CharacterStatistics> findByCharacterId(Long characterId);}
