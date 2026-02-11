package com.example.wemadeassignment.service;

import com.example.wemadeassignment.config.AnalysisProperties;
import com.example.wemadeassignment.domain.AnalysisResult;
import com.example.wemadeassignment.domain.AnalysisStatus;
import com.example.wemadeassignment.domain.IpInfo;
import com.example.wemadeassignment.parser.CsvLogParserImpl;
import com.example.wemadeassignment.repository.InMemoryAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CsvLogParser(실제) + AnalysisServiceImpl을 엮어 CSV 파일 기반 분석 흐름을 검증.
 * IpEnrichmentService만 stub으로 대체.
 */
class AnalysisServiceIntegrationTest {

    private AnalysisServiceImpl service;

    private static final IpInfo SAMPLE_IP_INFO = new IpInfo(
            "121.158.115.86", "AS4766", "Korea Telecom", "kt.com",
            "KR", "South Korea", "AS", "Asia");

    @BeforeEach
    void setUp() {
        CsvLogParserImpl csvLogParser = new CsvLogParserImpl(new AnalysisProperties(52428800L, 200000, 10));
        InMemoryAnalysisRepository repository = new InMemoryAnalysisRepository();
        AnalysisProperties properties = new AnalysisProperties(52428800L, 200000, 10);

        // IpEnrichmentService stub — 입력 IP 수만큼 SAMPLE 반환
        IpEnrichmentService ipEnrichmentStub = ips ->
                ips.stream().map(ip -> new IpInfo(ip, "AS0", "Test", "test.com",
                        "KR", "South Korea", "AS", "Asia")).toList();

        Executor syncExecutor = Runnable::run;

        service = new AnalysisServiceImpl(csvLogParser, ipEnrichmentStub,
                repository, properties, syncExecutor);
    }

    private MockMultipartFile loadCsvFile(String classpathLocation) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        return new MockMultipartFile("file", resource.getFilename(),
                "text/csv", resource.getInputStream());
    }

    @Test
    @DisplayName("normal.csv — 정상 파싱 후 집계 결과 검증")
    void normalCsvFullFlow() throws IOException {
        AnalysisResult result = service.submitAnalysis(loadCsvFile("csv/normal.csv"));

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getTotalRequests()).isEqualTo(2);
        assertThat(result.getParseErrorCount()).isZero();

        // IP 집계
        assertThat(result.getIpCounts()).containsEntry("121.158.115.86", 1L);
        assertThat(result.getIpCounts()).containsEntry("61.38.42.234", 1L);

        // Path 집계
        assertThat(result.getPathCounts()).containsEntry("/event/banner/mir2/popup", 1L);
        assertThat(result.getPathCounts()).containsEntry("/bbs/list/mir2free", 1L);

        // 상태 코드
        assertThat(result.getStatusCodeCounts()).containsEntry(200, 2L);

        // 상태 그룹 비율 — 전부 200이므로 2xx = 1.0
        assertThat(result.getStatusGroupRatios().get("2xx")).isEqualTo(1.0);

        // IP 정보 조회 결과
        assertThat(result.getTopIps()).hasSize(2);

        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("invalid-lines.csv — 파싱 오류가 있는 행 포함")
    void invalidLinesCsvFlow() throws IOException {
        AnalysisResult result = service.submitAnalysis(loadCsvFile("csv/invalid-lines.csv"));

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getTotalRequests()).isEqualTo(1);
        assertThat(result.getParseErrorCount()).isEqualTo(1);
        assertThat(result.getParseErrorSamples()).isNotEmpty();
    }

    @Test
    @DisplayName("quoted-useragent.csv — 따옴표가 포함된 UserAgent 처리")
    void quotedUserAgentCsvFlow() throws IOException {
        AnalysisResult result = service.submitAnalysis(loadCsvFile("csv/quoted-useragent.csv"));

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getTotalRequests()).isGreaterThanOrEqualTo(1);
        assertThat(result.getParseErrorCount()).isZero();
    }

    @Test
    @DisplayName("with-blank-lines.csv — 빈 줄이 포함된 CSV 처리")
    void blankLinesCsvFlow() throws IOException {
        AnalysisResult result = service.submitAnalysis(loadCsvFile("csv/with-blank-lines.csv"));

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getParseErrorCount()).isZero();
    }
}
