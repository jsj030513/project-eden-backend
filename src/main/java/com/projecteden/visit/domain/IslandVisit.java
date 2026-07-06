package com.projecteden.visit.domain;

import java.time.LocalDateTime;
import com.projecteden.user.domain.User;
import jakarta.persistence.*;

@Entity @Table(name = "island_visits")
public class IslandVisit {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private User visitor;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private User owner;
	private LocalDateTime visitedAt;
	protected IslandVisit() {}
	private IslandVisit(User visitor, User owner) { this.visitor = visitor; this.owner = owner; }
	public static IslandVisit create(User visitor, User owner) { return new IslandVisit(visitor, owner); }
	@PrePersist void prePersist() { visitedAt = LocalDateTime.now(); }
	public Long getId() { return id; }
	public User getVisitor() { return visitor; }
	public User getOwner() { return owner; }
	public LocalDateTime getVisitedAt() { return visitedAt; }
}
