package com.projecteden.character.domain;

import java.time.LocalDateTime;

import com.projecteden.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "characters")
public class Character {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CharacterGender gender;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private HairStyle hairStyle;

	@Column(nullable = false)
	private String hairColor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Outfit outfit;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CharacterJob job;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WeaponType weaponType;

	@Column(nullable = false)
	private int level = 1;

	@Column(nullable = false)
	private int exp = 0;

	@Column(nullable = false)
	private int energy = 100;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

	protected Character() {
	}

	private Character(
			User user,
			String name,
			CharacterGender gender,
			HairStyle hairStyle,
			String hairColor,
			Outfit outfit,
			CharacterJob job) {
		this.user = user;
		this.name = name;
		this.gender = gender;
		this.hairStyle = hairStyle;
		this.hairColor = hairColor;
		this.outfit = outfit;
		this.job = job;
		this.weaponType = job.getDefaultWeaponType();
	}

	public static Character create(
			User user,
			String name,
			CharacterGender gender,
			HairStyle hairStyle,
			String hairColor,
			Outfit outfit,
			CharacterJob job) {
		return new Character(user, name, gender, hairStyle, hairColor, outfit, job);
	}

	@PrePersist
	void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getName() {
		return name;
	}

	public CharacterGender getGender() {
		return gender;
	}

	public HairStyle getHairStyle() {
		return hairStyle;
	}

	public String getHairColor() {
		return hairColor;
	}

	public Outfit getOutfit() {
		return outfit;
	}

	public CharacterJob getJob() {
		return job;
	}

	public WeaponType getWeaponType() {
		return weaponType;
	}

	public int getLevel() {
		return level;
	}

	public int getExp() {
		return exp;
	}

	public int getEnergy() {
		return energy;
	}

	public void addExp(int amount) {
		exp += amount;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
