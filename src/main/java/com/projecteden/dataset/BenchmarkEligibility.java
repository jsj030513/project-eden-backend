package com.projecteden.dataset;

import java.util.List;

public record BenchmarkEligibility(boolean eligible, List<String> warnings) { }
