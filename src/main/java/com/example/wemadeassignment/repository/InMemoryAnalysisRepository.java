package com.example.wemadeassignment.repository;

import com.example.wemadeassignment.domain.AnalysisResult;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryAnalysisRepository implements AnalysisRepository {

    private final Map<String, AnalysisResult> store = new ConcurrentHashMap<>();

    @Override
    public void save(AnalysisResult result) {
        store.put(result.getAnalysisId(), result);
    }

    @Override
    public Optional<AnalysisResult> findById(String analysisId) {
        return Optional.ofNullable(store.get(analysisId));
    }
}
