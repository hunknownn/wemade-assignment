package com.example.wemadeassignment.service;

import com.example.wemadeassignment.config.AnalysisProperties;
import com.example.wemadeassignment.domain.AnalysisResult;
import com.example.wemadeassignment.domain.IpInfo;
import com.example.wemadeassignment.parser.CsvLogParser;
import com.example.wemadeassignment.parser.ParseStatistics;
import com.example.wemadeassignment.repository.AnalysisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import com.example.wemadeassignment.exception.ServerBusyException;
import java.util.concurrent.RejectedExecutionException;

@Service
public class AnalysisServiceImpl implements AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisServiceImpl.class);

    private final CsvLogParser csvLogParser;
    private final IpEnrichmentService ipEnrichmentService;
    private final AnalysisRepository analysisRepository;
    private final AnalysisProperties properties;
    private final Executor analysisExecutor;

    public AnalysisServiceImpl(CsvLogParser csvLogParser,
                               IpEnrichmentService ipEnrichmentService,
                               AnalysisRepository analysisRepository,
                               AnalysisProperties properties,
                               @Qualifier("analysisExecutor") Executor analysisExecutor) {
        this.csvLogParser = csvLogParser;
        this.ipEnrichmentService = ipEnrichmentService;
        this.analysisRepository = analysisRepository;
        this.properties = properties;
        this.analysisExecutor = analysisExecutor;
    }

    @Override
    public AnalysisResult submitAnalysis(MultipartFile file) {
        validateFile(file);

        String analysisId = UUID.randomUUID().toString();
        AnalysisResult result = new AnalysisResult(analysisId);
        analysisRepository.save(result);

        Path tempFile = saveTempFile(file);

        try {
            analysisExecutor.execute(() -> executeAnalysis(analysisId, tempFile));
        } catch (RejectedExecutionException e) {
            analysisRepository.deleteById(analysisId);
            deleteTempFile(tempFile);
            log.warn("분석 요청 거부: analysisId={}", analysisId, e);
            throw new ServerBusyException();
        }

        return result;
    }

    @Override
    public AnalysisResult getAnalysis(String analysisId) {
        return analysisRepository.findById(analysisId)
                .orElse(null);
    }

    private void executeAnalysis(String analysisId, Path tempFile) {
        long startTime = System.currentTimeMillis();
        log.info("분석 시작: analysisId={}", analysisId);

        AnalysisResult result = analysisRepository.findById(analysisId).orElseThrow();

        try (InputStream is = new BufferedInputStream(new FileInputStream(tempFile.toFile()))) {
            long loadElapsed = System.currentTimeMillis() - startTime;
            log.debug("임시 파일 로드 완료: analysisId={}, 소요시간={}ms", analysisId, loadElapsed);

            // 1. CSV 파싱 + 집계
            long parseStart = System.currentTimeMillis();
            LogAggregator aggregator = new LogAggregator();
            ParseStatistics stats = csvLogParser.parse(is, aggregator::aggregate);

            // 2. 집계 결과를 AnalysisResult에 반영
            result.setTotalRequests(aggregator.getTotalRequests());
            result.getStatusCodeCounts().putAll(aggregator.getStatusCodeCounts());
            result.getPathCounts().putAll(aggregator.getTopN(aggregator.getPathCounts(), properties.topN()));
            result.getIpCounts().putAll(aggregator.getTopN(aggregator.getIpCounts(), properties.topN()));
            long parseElapsed = System.currentTimeMillis() - parseStart;

            // 3. 상위 N개 IP에 대해 ipinfo 조회
            long enrichStart = System.currentTimeMillis();
            List<String> topIpList = aggregator.getTopN(aggregator.getIpCounts(), properties.topN())
                    .keySet().stream().toList();
            List<IpInfo> ipInfos = ipEnrichmentService.enrich(topIpList);
            result.setTopIps(ipInfos);
            long enrichElapsed = System.currentTimeMillis() - enrichStart;

            // 4. 파싱 오류 정보
            result.setParseErrorCount(stats.errorCount());
            result.setParseErrorSamples(stats.errorSamples());

            // 5. 상태 코드 그룹 비율 저장
            result.setStatusGroupRatios(aggregator.getStatusGroupRatios());

            result.complete();

            long totalElapsed = System.currentTimeMillis() - startTime;
            log.info("분석 완료: analysisId={}, 총 {}건, 파싱={}ms, IP조회={}ms, 전체={}ms",
                    analysisId, stats.totalLinesProcessed(), parseElapsed, enrichElapsed, totalElapsed);

        } catch (Exception e) {
            result.fail(e.getMessage());
            log.error("분석 실패: analysisId={}", analysisId, e);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("파일 검증 실패: 빈 파일");
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        if (file.getSize() > properties.maxFileSize()) {
            log.warn("파일 검증 실패: 크기 초과 ({}bytes > {}bytes)", file.getSize(), properties.maxFileSize());
            throw new IllegalArgumentException("파일 크기가 " + properties.maxFileSize() + " bytes를 초과합니다.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            log.warn("파일 검증 실패: 잘못된 확장자 ({})", filename);
            throw new IllegalArgumentException("CSV 파일만 업로드 가능합니다.");
        }
    }

    private Path saveTempFile(MultipartFile file) {
        try {
            long start = System.currentTimeMillis();
            Path tempFile = Files.createTempFile("analysis-", ".csv");
            file.transferTo(tempFile.toFile());
            log.debug("임시 파일 저장 완료: 크기={}bytes, 소요시간={}ms",
                    file.getSize(), System.currentTimeMillis() - start);
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("임시 파일 저장 실패", e);
        }
    }

    private void deleteTempFile(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패: {}", tempFile, e);
        }
    }
}
