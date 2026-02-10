package com.example.wemadeassignment.parser;

import com.example.wemadeassignment.config.AnalysisProperties;
import com.example.wemadeassignment.domain.AccessLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvLogParserImplTest {

    private CsvLogParserImpl parser;

    @BeforeEach
    void setUp() {
        parser = new CsvLogParserImpl(new AnalysisProperties(52428800, 200000, 10));
    }

    private InputStream loadCsv(String filename) {
        return getClass().getResourceAsStream("/csv/" + filename);
    }

    @Test
    @DisplayName("BOM + 헤더 + 정상 데이터 파싱")
    void parseBomHeaderAndData() {
        List<AccessLog> logs = new ArrayList<>();
        ParseStatistics stats = parser.parse(loadCsv("normal.csv"), logs::add);

        assertThat(stats.successCount()).isEqualTo(2);
        assertThat(stats.errorCount()).isEqualTo(0);
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).clientIp()).isEqualTo("121.158.115.86");
        assertThat(logs.get(0).httpStatus()).isEqualTo(200);

        System.out.println(logs.get(0).timeGenerated());
    }

    @Test
    @DisplayName("따옴표 내 쉼표가 포함된 UserAgent 파싱")
    void parseQuotedUserAgent() {
        List<AccessLog> logs = new ArrayList<>();
        ParseStatistics stats = parser.parse(loadCsv("quoted-useragent.csv"), logs::add);

        assertThat(stats.successCount()).isEqualTo(1);
        assertThat(logs.get(0).userAgent()).contains("KHTML, like Gecko");
    }

    @Test
    @DisplayName("빈 파일 처리")
    void parseEmptyFile() {
        InputStream empty = new java.io.ByteArrayInputStream(new byte[0]);
        ParseStatistics stats = parser.parse(empty, logs -> {});

        assertThat(stats.totalLinesProcessed()).isEqualTo(0);
        assertThat(stats.successCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("잘못된 형식 라인 스킵 + 오류 샘플 수집")
    void parseInvalidLineSkipped() {
        List<AccessLog> logs = new ArrayList<>();
        ParseStatistics stats = parser.parse(loadCsv("invalid-lines.csv"), logs::add);

        assertThat(stats.successCount()).isEqualTo(1);
        assertThat(stats.errorCount()).isEqualTo(1);
        assertThat(stats.errorSamples()).hasSize(1);
        assertThat(stats.errorSamples().get(0).reason()).contains("컬럼 수 불일치");
    }

    @Test
    @DisplayName("숫자 변환 실패 시 오류 처리")
    void parseNumberFormatError() {
        List<AccessLog> logs = new ArrayList<>();
        ParseStatistics stats = parser.parse(loadCsv("number-format-error.csv"), logs::add);

        assertThat(stats.errorCount()).isEqualTo(1);
        assertThat(stats.errorSamples().get(0).reason()).contains("숫자 변환 실패");
    }

    @Test
    @DisplayName("maxLines 제한")
    void parseMaxLinesLimit() {
        CsvLogParserImpl limitedParser = new CsvLogParserImpl(new AnalysisProperties(52428800, 2, 10));

        List<AccessLog> logs = new ArrayList<>();
        ParseStatistics stats = limitedParser.parse(loadCsv("maxlines.csv"), logs::add);

        assertThat(stats.totalLinesProcessed()).isEqualTo(2);
        assertThat(logs).hasSize(2);
    }

    @Test
    @DisplayName("빈 줄 무시")
    void parseSkipsBlankLines() {
        List<AccessLog> logs = new ArrayList<>();
        ParseStatistics stats = parser.parse(loadCsv("with-blank-lines.csv"), logs::add);

        assertThat(stats.totalLinesProcessed()).isEqualTo(1);
        assertThat(stats.successCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("빈 SslProtocol 필드 허용")
    void parseEmptySslProtocol() {
        List<AccessLog> logs = new ArrayList<>();
        ParseStatistics stats = parser.parse(loadCsv("empty-ssl.csv"), logs::add);

        assertThat(stats.successCount()).isEqualTo(1);
        assertThat(logs.get(0).sslProtocol()).isEmpty();
    }

    @Test
    @DisplayName("splitCsvLine — 따옴표 이스케이프 처리")
    void splitCsvLineEscapedQuotes() {
        List<String> fields = parser.splitCsvLine("\"say \"\"hi\"\"\",normal");

        assertThat(fields).containsExactly("say \"hi\"", "normal");
    }
}
