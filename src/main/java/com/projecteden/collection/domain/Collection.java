package com.projecteden.collection.domain;

import java.time.LocalDateTime;

import com.projecteden.ai.domain.RecognizedObject;
import com.projecteden.character.domain.Character;

import jakarta.persistence.*;

@Entity
@Table(name = "collections", uniqueConstraints = @UniqueConstraint(columnNames = {"character_id", "recognized_object"}))
public class Collection {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "character_id", nullable = false) private Character character;
	@Enumerated(EnumType.STRING) @Column(name = "recognized_object", nullable = false) private RecognizedObject recognizedObject;
	@Enumerated(EnumType.STRING) @Column(nullable = false) private Rarity rarity;
	@Column(nullable = false) private int discoveredCount;
	@Column(nullable = false) private LocalDateTime firstDiscoveredAt;
	@Column(nullable = false) private LocalDateTime lastDiscoveredAt;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	protected Collection() {}
	private Collection(Character character, RecognizedObject object, Rarity rarity, LocalDateTime now) { this.character=character; this.recognizedObject=object; this.rarity=rarity; this.discoveredCount=1; this.firstDiscoveredAt=now; this.lastDiscoveredAt=now; }
	public static Collection create(Character character, RecognizedObject object, Rarity rarity, LocalDateTime now) { return new Collection(character, object, rarity, now); }
	public void rediscover(LocalDateTime now) { discoveredCount++; lastDiscoveredAt=now; }
	@PrePersist void prePersist(){LocalDateTime now=LocalDateTime.now();createdAt=now;updatedAt=now;}
	@PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
	public Long getId(){return id;} public Character getCharacter(){return character;} public RecognizedObject getRecognizedObject(){return recognizedObject;} public Rarity getRarity(){return rarity;} public int getDiscoveredCount(){return discoveredCount;} public LocalDateTime getFirstDiscoveredAt(){return firstDiscoveredAt;} public LocalDateTime getLastDiscoveredAt(){return lastDiscoveredAt;}
}
