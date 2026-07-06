package com.projecteden.cheer.domain;
import java.time.LocalDateTime;
import com.projecteden.user.domain.User;
import jakarta.persistence.*;
@Entity @Table(name = "cheers")
public class Cheer {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private User sender;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private User receiver;
	private LocalDateTime cheeredAt;
	protected Cheer() {}
	private Cheer(User sender, User receiver) { this.sender = sender; this.receiver = receiver; }
	public static Cheer create(User sender, User receiver) { return new Cheer(sender, receiver); }
	@PrePersist void prePersist() { cheeredAt = LocalDateTime.now(); }
	public Long getId() { return id; }
	public User getSender() { return sender; }
	public User getReceiver() { return receiver; }
	public LocalDateTime getCheeredAt() { return cheeredAt; }
}
