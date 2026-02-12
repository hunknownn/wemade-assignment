package com.example.wemadeassignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "에러 응답")
public record ErrorResponse(
        @Schema(description = "HTTP 상태 코드", example = "400")
        int status,

        @Schema(description = "에러 유형", example = "Bad Request")
        String error,

        @Schema(description = "에러 메시지", example = "잘못된 분석 ID 형식입니다.")
        String message,

        @Schema(description = "에러 발생 시각", example = "2025-01-15T10:30:00")
        LocalDateTime timestamp
) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, LocalDateTime.now());
    }
}
