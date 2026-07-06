package com.projecteden.ranking.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.projecteden.ranking.domain.Ranking;
import com.projecteden.user.domain.User;
public interface RankingRepository extends JpaRepository<Ranking, Long> { Optional<Ranking> findByUser(User user); }
