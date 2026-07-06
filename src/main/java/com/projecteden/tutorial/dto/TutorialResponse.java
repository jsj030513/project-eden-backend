package com.projecteden.tutorial.dto;

import com.projecteden.tutorial.domain.TutorialStep;

public record TutorialResponse(TutorialStep currentStep, boolean completed) {
}
