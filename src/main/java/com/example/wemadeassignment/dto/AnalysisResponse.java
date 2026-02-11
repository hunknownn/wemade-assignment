package com.example.wemadeassignment.dto;

import com.example.wemadeassignment.domain.AnalysisResult;
import com.example.wemadeassignment.domain.AnalysisStatus;
import com.example.wemadeassignment.domain.IpInfo;
import com.example.wemadeassignment.domain.ParseErrorSample;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 분석 결과 조회 응답.
 * PROCESSING 상태에서는 집계 필드가 null로 반환된다.
 */
public record AnalysisResponse(
        String analysisId,
        AnalysisStatus status,
        Long totalRequests,
        Map<Integer, Long> statusCodeCounts,
        Map<String, Double> statusGroupRatios,
        Map<String, Long> topPaths,
        Map<String, Long> topIps,
        List<IpInfo> ipDetails,
        Integer parseErrorCount,
        List<ParseErrorSample> parseErrorSamples,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
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
