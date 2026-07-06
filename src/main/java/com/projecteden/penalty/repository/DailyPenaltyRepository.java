package com.projecteden.penalty.repository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.projecteden.penalty.domain.DailyPenalty;
import com.projecteden.user.domain.User;
public interface DailyPenaltyRepository extends JpaRepository<DailyPenalty, Long> { Optional<DailyPenalty> findByUser(User user); }
