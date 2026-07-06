package com.projecteden.cheer.repository;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import com.projecteden.cheer.domain.Cheer;
import com.projecteden.user.domain.User;
public interface CheerRepository extends JpaRepository<Cheer, Long> { boolean existsBySenderAndReceiverAndCheeredAtBetween(User sender, User receiver, LocalDateTime start, LocalDateTime end); }
