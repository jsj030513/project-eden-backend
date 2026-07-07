package com.projecteden.evolution.domain;
import java.time.LocalDateTime;import com.projecteden.character.domain.Character;import jakarta.persistence.*;
@Entity@Table(name="evolution_histories")public class EvolutionHistory{
	@Id@GeneratedValue(strategy=GenerationType.IDENTITY)private Long id;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="character_id",nullable=false)private Character character;@Enumerated(EnumType.STRING)@Column(nullable=false)private EvolutionEventType eventType;@Column(nullable=false)private String description;@Column(nullable=false)private LocalDateTime createdAt;
	protected EvolutionHistory(){}private EvolutionHistory(Character character,EvolutionEventType type,String description,LocalDateTime now){this.character=character;this.eventType=type;this.description=description;this.createdAt=now;}public static EvolutionHistory create(Character character,EvolutionEventType type,String description,LocalDateTime now){return new EvolutionHistory(character,type,description,now);}
	public Long getId(){return id;}public Character getCharacter(){return character;}public EvolutionEventType getEventType(){return eventType;}public String getDescription(){return description;}public LocalDateTime getCreatedAt(){return createdAt;}
}
