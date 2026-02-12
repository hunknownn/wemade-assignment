package com.example.wemadeassignment.dto;

import com.example.wemadeassignment.domain.AnalysisResult;
import com.example.wemadeassignment.domain.AnalysisStatus;
import com.example.wemadeassignment.domain.IpInfo;
import com.example.wemadeassignment.domain.ParseErrorSample;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 분석 결과 조회 응답.
 * PROCESSING 상태에서는 집계 필드가 null로 반환된다.
 */
@Schema(description = "분석 결과 조회 응답. PROCESSING 상태에서는 집계 필드가 null로 반환된다.")
public record AnalysisResponse(
        @Schema(description = "분석 ID (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
        String analysisId,

        @Schema(description = "분석 상태")
        AnalysisStatus status,

        @Schema(description = "총 요청 수", example = "15234", nullable = true)
        Long totalRequests,

        @Schema(description = "HTTP 상태 코드별 요청 수", example = "{\"200\":12000,\"404\":500,\"500\":34}", nullable = true)
        Map<Integer, Long> statusCodeCounts,

        @Schema(description = "HTTP 상태 그룹별 비율 (0~1)", example = "{\"2xx\":0.788,\"4xx\":0.185,\"5xx\":0.027}", nullable = true)
        Map<String, Double> statusGroupRatios,

        @Schema(description = "요청이 많은 상위 경로별 요청 수", example = "{\"/api/users\":3200,\"/api/login\":2100}", nullable = true)
        Map<String, Long> topPaths,

        @Schema(description = "요청이 많은 상위 IP별 요청 수", example = "{\"192.168.1.1\":450,\"10.0.0.1\":320}", nullable = true)
        Map<String, Long> topIps,

        @Schema(description = "상위 IP의 상세 정보 (ipinfo 조회 결과)", nullable = true)
        List<IpInfo> ipDetails,

        @Schema(description = "파싱 오류 건수", example = "3", nullable = true)
        Integer parseErrorCount,

        @Schema(description = "파싱 오류 샘플 (최대 10건)", nullable = true)
        List<ParseErrorSample> parseErrorSamples,

        @Schema(description = "분석 요청 생성 시각", example = "2025-01-15T10:30:00")
        LocalDateTime createdAt,

        @Schema(description = "분석 완료 시각", example = "2025-01-15T10:30:05", nullable = true)
        LocalDateTime completedAt,

        @Schema(description = "분석 실패 사유 (FAILED 상태일 때만 존재)", example = "CSV 파싱 중 오류 발생", nullable = true)
        String failureReason
) {
    public static AnalysisResponse from(AnalysisResult result) {
        if (result.getStatus() == AnalysisStatus.PROCESSING) {
            return new AnalysisResponse(
                    result.getAnalysisId(),
                    result.getStatus(),
                    null, null, null, null, null, null, null, null,
                    result.getCreatedAt(),
                    null,
                    null
            );
        }

        return new AnalysisResponse(
                result.getAnalysisId(),
                result.getStatus(),
                result.getTotalRequests(),
                Map.copyOf(result.getStatusCodeCounts()),
                Map.copyOf(result.getStatusGroupRatios()),
                Map.copyOf(result.getPathCounts()),
                Map.copyOf(result.getIpCounts()),
                List.copyOf(result.getTopIps()),
                result.getParseErrorCount(),
                List.copyOf(result.getParseErrorSamples()),
                result.getCreatedAt(),
                result.getCompletedAt(),
                result.getFailureReason()
        );
    }
}
