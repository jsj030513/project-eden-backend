package com.projecteden.memorytaxonomy.evaluation;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ImageEvaluationApplicationRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ImageEvaluationApplicationRunner.class);

	private final ImageEvaluationProperties properties;
	private final ImageEvaluationRunner runner;

	public ImageEvaluationApplicationRunner(
			ImageEvaluationProperties properties,
			ImageEvaluationRunner runner) {
		this.properties = properties;
		this.runner = runner;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isEnabled()) {
			return;
		}
		if (properties.getManifest() == null || properties.getManifest().isBlank()) {
			log.warn("Image evaluation is enabled but manifest is not configured. Skipping evaluation.");
			return;
		}
		if (properties.getOutputDirectory() == null || properties.getOutputDirectory().isBlank()) {
			log.warn("Image evaluation is enabled but output directory is not configured. Skipping evaluation.");
			return;
		}
		ImageEvaluationSummary summary = runner.run(
				Path.of(properties.getManifest()),
				Path.of(properties.getOutputDirectory()),
				properties.getMaxCases());
		log.info("Image evaluation completed. totalCases={} primaryAccuracy={}",
				summary.totalCases(),
				summary.primaryAccuracy());
	}
}
