package com.projecteden.friend.domain;

import java.time.LocalDateTime;

import com.projecteden.user.domain.User;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "friends")
public class Friend {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false)
	private User requester;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false)
	private User receiver;
	@Enumerated(EnumType.STRING)
	private FriendStatus status = FriendStatus.PENDING;
	private LocalDateTime requestedAt;
	private LocalDateTime acceptedAt;
	protected Friend() {}
	private Friend(User requester, User receiver) { this.requester = requester; this.receiver = receiver; }
	public static Friend create(User requester, User receiver) { return new Friend(requester, receiver); }
	@PrePersist void prePersist() { if (requestedAt == null) requestedAt = LocalDateTime.now(); }
	public void accept() { status = FriendStatus.ACCEPTED; acceptedAt = LocalDateTime.now(); }
	public Long getId() { return id; }
	public User getRequester() { return requester; }
	public User getReceiver() { return receiver; }
	public FriendStatus getStatus() { return status; }
	public LocalDateTime getRequestedAt() { return requestedAt; }
	public LocalDateTime getAcceptedAt() { return acceptedAt; }
}
