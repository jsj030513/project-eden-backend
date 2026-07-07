package com.projecteden.achievement.domain;
import java.time.LocalDateTime;
import jakarta.persistence.*;
@Entity @Table(name="achievements")
public class Achievement{
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@Column(nullable=false,unique=true) private String code; @Column(nullable=false) private String name; @Column(nullable=false) private String description; @Enumerated(EnumType.STRING) @Column(nullable=false) private AchievementType type; @Column(nullable=false) private int requiredValue; private String rewardTitleCode; private LocalDateTime createdAt; private LocalDateTime updatedAt;
	protected Achievement(){} private Achievement(String code,String name,String description,AchievementType type,int requiredValue,String rewardTitleCode){this.code=code;this.name=name;this.description=description;this.type=type;this.requiredValue=requiredValue;this.rewardTitleCode=rewardTitleCode;} public static Achievement create(String code,String name,String description,AchievementType type,int requiredValue,String rewardTitleCode){return new Achievement(code,name,description,type,requiredValue,rewardTitleCode);}
	@PrePersist void prePersist(){LocalDateTime now=LocalDateTime.now();createdAt=now;updatedAt=now;} @PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
	public Long getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public String getDescription(){return description;} public AchievementType getType(){return type;} public int getRequiredValue(){return requiredValue;} public String getRewardTitleCode(){return rewardTitleCode;}
}
