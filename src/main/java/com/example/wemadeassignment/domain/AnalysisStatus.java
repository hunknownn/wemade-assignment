package com.example.wemadeassignment.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "분석 상태", enumAsRef = true)
public enum AnalysisStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
