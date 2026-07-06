package com.projecteden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProjectEdenBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProjectEdenBackendApplication.class, args);
	}

}
