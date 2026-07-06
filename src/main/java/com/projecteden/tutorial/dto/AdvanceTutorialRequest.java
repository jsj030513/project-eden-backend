package com.projecteden.tutorial.dto;

import com.projecteden.tutorial.domain.TutorialStep;

import jakarta.validation.constraints.NotNull;

public record AdvanceTutorialRequest(@NotNull TutorialStep nextStep) {
}
