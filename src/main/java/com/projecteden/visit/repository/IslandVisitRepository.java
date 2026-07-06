package com.projecteden.visit.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import com.projecteden.user.domain.User;
import com.projecteden.visit.domain.IslandVisit;
public interface IslandVisitRepository extends JpaRepository<IslandVisit, Long> {
	@EntityGraph(attributePaths = "owner")
	List<IslandVisit> findByVisitorOrderByVisitedAtDesc(User visitor);
}
