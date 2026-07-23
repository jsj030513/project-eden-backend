package com.projecteden.world.ecology;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface WorldPlacedObjectRepository extends JpaRepository<WorldPlacedObject, Long> {
    List<WorldPlacedObject> findByWorldChangeId(Long worldChangeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select object from WorldPlacedObject object join fetch object.worldChange change join fetch change.character where object.id = :id")
    Optional<WorldPlacedObject> findByIdForUpdate(@Param("id") Long id);
}
