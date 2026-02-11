package com.example.wemadeassignment.dto;

/**
 * 분석 요청 제출 응답 — 202 Accepted와 함께 반환.
 */
public record AnalysisSubmitResponse(
        String analysisId,
        String status,
        String message
) {
    public static AnalysisSubmitResponse of(String analysisId, String status) {
        return new AnalysisSubmitResponse(analysisId, status, "분석 요청이 접수되었습니다.");
    }
}
