package com.projecteden.evolution.domain;

import java.time.LocalDateTime;import com.projecteden.character.domain.Character;import jakarta.persistence.*;
@Entity @Table(name="world_evolutions") public class WorldEvolution{
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;@OneToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="character_id",nullable=false,unique=true)private Character character;@Column(nullable=false)private int worldLevel=1;@Column(nullable=false)private int evolutionPoint;@Enumerated(EnumType.STRING)@Column(nullable=false)private WorldStage worldStage=WorldStage.SEED;private LocalDateTime createdAt;private LocalDateTime updatedAt;
	protected WorldEvolution(){}private WorldEvolution(Character character){this.character=character;}public static WorldEvolution create(Character character){return new WorldEvolution(character);}public void addPoint(int point){evolutionPoint+=point;}public void evolve(int level,WorldStage stage){worldLevel=level;worldStage=stage;}
	@PrePersist void prePersist(){LocalDateTime now=LocalDateTime.now();createdAt=now;updatedAt=now;}@PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
	public Long getId(){return id;}public Character getCharacter(){return character;}public int getWorldLevel(){return worldLevel;}public int getEvolutionPoint(){return evolutionPoint;}public WorldStage getWorldStage(){return worldStage;}
}
