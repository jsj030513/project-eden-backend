package com.projecteden.dataset;

import java.time.Instant;

public record EvaluationProgress(int total, int processed, int failed, Instant startedAt, Instant finishedAt) {
}
