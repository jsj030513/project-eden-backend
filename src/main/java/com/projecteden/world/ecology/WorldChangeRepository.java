package com.projecteden.world.ecology;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WorldChangeRepository extends JpaRepository<WorldChange, Long> { Optional<WorldChange> findByRecognitionId(Long recognitionId); Optional<WorldChange> findByTargetObjectId(Long targetObjectId); boolean existsByTargetObjectId(Long targetObjectId); java.util.List<WorldChange> findByCharacterIdOrderByIdAsc(Long characterId); Optional<WorldChange> findByCharacterIdAndMessageKey(Long characterId, String messageKey); }
