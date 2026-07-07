package com.projecteden.title.domain;
import java.time.LocalDateTime; import jakarta.persistence.*;
@Entity @Table(name="titles") public class Title{
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(nullable=false,unique=true) private String code; @Column(nullable=false) private String name; @Column(nullable=false) private String description; private LocalDateTime createdAt; private LocalDateTime updatedAt;
	protected Title(){} private Title(String code,String name,String description){this.code=code;this.name=name;this.description=description;} public static Title create(String code,String name,String description){return new Title(code,name,description);}
	@PrePersist void prePersist(){LocalDateTime now=LocalDateTime.now();createdAt=now;updatedAt=now;} @PreUpdate void preUpdate(){updatedAt=LocalDateTime.now();}
	public Long getId(){return id;} public String getCode(){return code;} public String getName(){return name;} public String getDescription(){return description;}
}
