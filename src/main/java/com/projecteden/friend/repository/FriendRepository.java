package com.projecteden.friend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.projecteden.friend.domain.Friend;
import com.projecteden.friend.domain.FriendStatus;
import com.projecteden.user.domain.User;

public interface FriendRepository extends JpaRepository<Friend, Long> {
	boolean existsByRequesterAndReceiver(User requester, User receiver);
	@Query("select f from Friend f where (f.requester = :first or f.receiver = :second) and f.status = :status")
	@EntityGraph(attributePaths = {"requester", "receiver"})
	List<Friend> findByRequesterOrReceiverAndStatus(@Param("first") User first, @Param("second") User second, @Param("status") FriendStatus status);

	@Query("select (count(f) > 0) from Friend f where f.status = 'ACCEPTED' and ((f.requester = :first and f.receiver = :second) or (f.requester = :second and f.receiver = :first))")
	boolean existsAcceptedBetween(@Param("first") User first, @Param("second") User second);
	List<Friend> findByReceiverAndStatus(User receiver, FriendStatus status);
}
