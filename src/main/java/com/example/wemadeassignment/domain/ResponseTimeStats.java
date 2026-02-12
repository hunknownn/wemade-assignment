package com.example.wemadeassignment.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 응답 시간 통계 (단위: 초).
 */
@Schema(description = "클라이언트 응답 시간 통계 (단위: 초)")
public record ResponseTimeStats(
        @Schema(description = "최솟값", example = "0.001")
        double min,

        @Schema(description = "최댓값", example = "12.345")
        double max,

        @Schema(description = "평균", example = "1.234")
        double avg,

        @Schema(description = "50번째 퍼센타일 (중앙값)", example = "0.512")
        double p50,

        @Schema(description = "95번째 퍼센타일", example = "3.456")
        double p95,

        @Schema(description = "99번째 퍼센타일", example = "8.901")
        double p99
) {
}
