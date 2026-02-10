package com.example.wemadeassignment.domain;

/**
 * CSV 접속 로그 한 줄의 파싱 결과. 필드 순서는 CSV 헤더와 동일.
 */
public record AccessLog(
        String timeGenerated,
        String clientIp,
        String httpMethod,
        String requestUri,
        String userAgent,
        int httpStatus,
        String httpVersion,
        long receivedBytes,
        long sentBytes,
        double clientResponseTime,
        String sslProtocol,
        String originalRequestUriWithArgs
) {
}
