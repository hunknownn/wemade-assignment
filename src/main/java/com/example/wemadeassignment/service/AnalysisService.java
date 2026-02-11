package com.example.wemadeassignment.service;

import com.example.wemadeassignment.domain.AnalysisResult;
import org.springframework.web.multipart.MultipartFile;

public interface AnalysisService {

    /** 분석 요청 제출 — analysisId를 생성하고 비동기 분석을 시작한다 */
    AnalysisResult submitAnalysis(MultipartFile file);

    /** 분석 결과 조회 */
    AnalysisResult getAnalysis(String analysisId);
}
