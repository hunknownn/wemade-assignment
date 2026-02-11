package com.example.wemadeassignment.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "CSV 파싱 오류 샘플")
public record ParseErrorSample(
        @Schema(description = "오류가 발생한 줄 번호", example = "42")
        int lineNumber,

        @Schema(description = "오류가 발생한 원본 줄 내용", example = "invalid,,line,data")
        String line,

        @Schema(description = "오류 원인", example = "필드 수 불일치: 기대 7개, 실제 4개")
        String reason
) {
}
