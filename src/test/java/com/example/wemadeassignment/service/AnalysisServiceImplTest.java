package com.example.wemadeassignment.service;

import com.example.wemadeassignment.config.AnalysisProperties;
import com.example.wemadeassignment.domain.AnalysisResult;
import com.example.wemadeassignment.domain.AnalysisStatus;
import com.example.wemadeassignment.domain.IpInfo;
import com.example.wemadeassignment.domain.ParseErrorSample;
import com.example.wemadeassignment.parser.CsvLogParser;
import com.example.wemadeassignment.parser.ParseStatistics;
import com.example.wemadeassignment.repository.AnalysisRepository;
import com.example.wemadeassignment.repository.InMemoryAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceImplTest {

    @Mock
    private CsvLogParser csvLogParser;

    @Mock
    private IpEnrichmentService ipEnrichmentService;

    private AnalysisRepository analysisRepository;
    private AnalysisProperties properties;

    // 동기 실행 executor — 테스트에서 즉시 실행
    private final Executor syncExecutor = Runnable::run;

    private AnalysisServiceImpl service;

    @BeforeEach
    void setUp() {
        analysisRepository = new InMemoryAnalysisRepository();
        properties = new AnalysisProperties(52428800L, 200000, 10);
        service = new AnalysisServiceImpl(csvLogParser, ipEnrichmentService,
                analysisRepository, properties, syncExecutor);
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes());
    }

    @Test
    @DisplayName("정상 제출 시 PROCESSING 상태의 AnalysisResult 반환")
    void submitAnalysisReturnsProcessingResult() {
        when(csvLogParser.parse(any(), any()))
                .thenReturn(new ParseStatistics(0, 0, 0, List.of()));
        when(ipEnrichmentService.enrich(any())).thenReturn(List.of());

        AnalysisResult result = service.submitAnalysis(csvFile("header\ndata"));

        assertThat(result).isNotNull();
        assertThat(result.getAnalysisId()).isNotBlank();
    }

    @Test
    @DisplayName("동기 executor 사용 시 제출 즉시 COMPLETED")
    void submitWithSyncExecutorCompletesImmediately() {
        when(csvLogParser.parse(any(), any()))
                .thenReturn(new ParseStatistics(1, 1, 0, List.of()));
        when(ipEnrichmentService.enrich(any())).thenReturn(List.of());

        AnalysisResult result = service.submitAnalysis(csvFile("header\ndata"));

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("파싱 오류 정보가 결과에 반영")
    void parseErrorsReflected() {
        List<ParseErrorSample> errors = List.of(
                new ParseErrorSample(2, "bad,line", "필드 수 불일치"));
        when(csvLogParser.parse(any(), any()))
                .thenReturn(new ParseStatistics(2, 1, 1, errors));
        when(ipEnrichmentService.enrich(any())).thenReturn(List.of());

        AnalysisResult result = service.submitAnalysis(csvFile("header\ndata"));

        assertThat(result.getParseErrorCount()).isEqualTo(1);
        assertThat(result.getParseErrorSamples()).hasSize(1);
    }

    @Test
    @DisplayName("getAnalysis — 존재하는 ID 조회")
    void getAnalysisFound() {
        when(csvLogParser.parse(any(), any()))
                .thenReturn(new ParseStatistics(0, 0, 0, List.of()));
        when(ipEnrichmentService.enrich(any())).thenReturn(List.of());

        AnalysisResult submitted = service.submitAnalysis(csvFile("header\ndata"));
        AnalysisResult found = service.getAnalysis(submitted.getAnalysisId());

        assertThat(found).isNotNull();
        assertThat(found.getAnalysisId()).isEqualTo(submitted.getAnalysisId());
    }

    @Test
    @DisplayName("getAnalysis — 존재하지 않는 ID 조회 시 null")
    void getAnalysisNotFound() {
        assertThat(service.getAnalysis("non-existent")).isNull();
    }

    @Test
    @DisplayName("빈 파일 제출 시 예외")
    void submitEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> service.submitAnalysis(empty))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 파일 제출 시 예외")
    void submitNullFile() {
        assertThatThrownBy(() -> service.submitAnalysis(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("파일 크기 초과 시 예외")
    void submitOversizedFile() {
        AnalysisProperties smallLimit = new AnalysisProperties(10L, 200000, 10);
        AnalysisServiceImpl smallService = new AnalysisServiceImpl(
                csvLogParser, ipEnrichmentService, analysisRepository, smallLimit, syncExecutor);

        MockMultipartFile bigFile = new MockMultipartFile("file", "test.csv", "text/csv",
                "a]".repeat(20).getBytes());

        assertThatThrownBy(() -> smallService.submitAnalysis(bigFile))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("파싱 중 예외 발생 시 FAILED 상태")
    void parsingExceptionCausesFailed() {
        when(csvLogParser.parse(any(), any()))
                .thenThrow(new RuntimeException("파싱 오류"));

        AnalysisResult result = service.submitAnalysis(csvFile("header\ndata"));

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(result.getFailureReason()).isEqualTo("파싱 오류");
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("executor 거부 시 ServerBusyException 발생 및 repository에서 삭제")
    void rejectedExecutionThrowsServerBusyException() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("풀 가득 참");
        };
        AnalysisServiceImpl rejectService = new AnalysisServiceImpl(
                csvLogParser, ipEnrichmentService, analysisRepository, properties, rejectingExecutor);

        assertThatThrownBy(() -> rejectService.submitAnalysis(csvFile("header\ndata")))
                .isInstanceOf(com.example.wemadeassignment.exception.ServerBusyException.class);
    }

    @Test
    @DisplayName("CSV가 아닌 파일 제출 시 예외")
    void submitNonCsvFile() {
        MockMultipartFile txtFile = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());

        assertThatThrownBy(() -> service.submitAnalysis(txtFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CSV");
    }

    @Test
    @DisplayName("비동기 executor 사용 시 제출 직후 PROCESSING")
    void submitWithAsyncExecutorReturnsProcessing() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Executor asyncExecutor = Executors.newSingleThreadExecutor();

        when(csvLogParser.parse(any(), any())).thenAnswer(invocation -> {
            latch.await(5, TimeUnit.SECONDS);
            return new ParseStatistics(0, 0, 0, List.of());
        });
        when(ipEnrichmentService.enrich(any())).thenReturn(List.of());

        AnalysisServiceImpl asyncService = new AnalysisServiceImpl(
                csvLogParser, ipEnrichmentService, analysisRepository, properties, asyncExecutor);

        AnalysisResult result = asyncService.submitAnalysis(csvFile("header\ndata"));

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);

        latch.countDown();
        Thread.sleep(500);

        AnalysisResult completed = asyncService.getAnalysis(result.getAnalysisId());
        assertThat(completed.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    @DisplayName("ipEnrichmentService.enrich()가 호출됨")
    void ipEnrichmentServiceCalled() {
        when(csvLogParser.parse(any(), any()))
                .thenReturn(new ParseStatistics(1, 1, 0, List.of()));
        when(ipEnrichmentService.enrich(any())).thenReturn(List.of());

        service.submitAnalysis(csvFile("header\ndata"));

        verify(ipEnrichmentService).enrich(any());
    }
}
