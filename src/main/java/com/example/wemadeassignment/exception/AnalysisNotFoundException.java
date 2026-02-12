package com.example.wemadeassignment.exception;

public class AnalysisNotFoundException extends RuntimeException {

    public AnalysisNotFoundException(String analysisId) {
        super("분석 결과를 찾을 수 없습니다: " + analysisId);
    }
}
