package com.example.wemadeassignment.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 분석 결과를 담는 가변 객체.
 * 비동기 스레드에서 상태를 갱신하므로 status는 volatile, 집계 Map은 ConcurrentHashMap 사용.
 */
@Getter
@Setter
public class AnalysisResult {

    private final String analysisId;
    private volatile AnalysisStatus status;
    private long totalRequests;

    private final Map<Integer, Long> statusCodeCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> pathCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> ipCounts = new ConcurrentHashMap<>();

    private List<IpInfo> topIps = new ArrayList<>();
    private Map<String, Double> statusGroupRatios = new ConcurrentHashMap<>();
    private int parseErrorCount;
    private List<ParseErrorSample> parseErrorSamples = new ArrayList<>();

    private final LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String failureReason;

    public AnalysisResult(String analysisId) {
        this.analysisId = analysisId;
        this.status = AnalysisStatus.PROCESSING;
        this.createdAt = LocalDateTime.now();
    }

    public void complete() {
        this.completedAt = LocalDateTime.now();
        this.status = AnalysisStatus.COMPLETED;
    }

    public void fail(String reason) {
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
        this.status = AnalysisStatus.FAILED;
    }
}
