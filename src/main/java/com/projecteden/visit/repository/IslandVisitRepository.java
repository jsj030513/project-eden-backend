package com.projecteden.visit.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.projecteden.user.domain.User;
import com.projecteden.visit.domain.IslandVisit;
public interface IslandVisitRepository extends JpaRepository<IslandVisit, Long> { List<IslandVisit> findByVisitor(User visitor); }
