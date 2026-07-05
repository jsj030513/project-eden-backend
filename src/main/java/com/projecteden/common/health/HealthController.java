package com.projecteden.common.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of(
				"status", "OK",
				"service", "Project Eden Backend",
				"version", "0.0.1");
	}
}
