package com.projecteden.dataset;
import java.time.Instant;
public record BenchmarkFailure(BenchmarkFailureCode code,String safeMessage,Instant failedAt,int processedCases,int totalCases) { }
