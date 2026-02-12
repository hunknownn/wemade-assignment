package com.example.wemadeassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 분석 요청 제출 응답 — 202 Accepted와 함께 반환.
 */
@Schema(description = "분석 요청 제출 응답")
public record AnalysisSubmitResponse(
        @Schema(description = "분석 ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        String analysisId,

        @Schema(description = "분석 상태", example = "PROCESSING")
        String status,

        @Schema(description = "응답 메시지", example = "분석 요청이 접수되었습니다.")
        String message
) {
    public static AnalysisSubmitResponse of(String analysisId, String status) {
        return new AnalysisSubmitResponse(analysisId, status, "분석 요청이 접수되었습니다.");
    }
}
