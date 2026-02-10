package com.example.wemadeassignment.parser;

import com.example.wemadeassignment.domain.ParseErrorSample;

import java.util.List;

/** CSV 파싱 완료 후 반환되는 통계 정보 */
public record ParseStatistics(
        int totalLinesProcessed,
        int successCount,
        int errorCount,
        List<ParseErrorSample> errorSamples
) {
}
