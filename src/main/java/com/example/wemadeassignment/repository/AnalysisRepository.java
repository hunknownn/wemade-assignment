package com.example.wemadeassignment.repository;

import com.example.wemadeassignment.domain.AnalysisResult;

import java.util.Optional;

public interface AnalysisRepository {

    void save(AnalysisResult result);

    Optional<AnalysisResult> findById(String analysisId);

    void deleteById(String analysisId);
}
