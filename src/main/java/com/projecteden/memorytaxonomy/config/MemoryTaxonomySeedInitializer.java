package com.projecteden.memorytaxonomy.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MemoryTaxonomySeedInitializer implements ApplicationRunner {

	private final MemoryTaxonomySeeder seeder;

	public MemoryTaxonomySeedInitializer(MemoryTaxonomySeeder seeder) {
		this.seeder = seeder;
	}

	@Override
	public void run(ApplicationArguments args) {
		seeder.seed();
	}
}
