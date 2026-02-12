package com.example.wemadeassignment.parser;

import com.example.wemadeassignment.config.AnalysisProperties;
import com.example.wemadeassignment.domain.AccessLog;
import com.example.wemadeassignment.domain.ParseErrorSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
public class CsvLogParserImpl implements CsvLogParser {

    private static final Logger log = LoggerFactory.getLogger(CsvLogParserImpl.class);
    private static final int EXPECTED_COLUMNS = 12;
    private static final int MAX_ERROR_SAMPLES = 10;
    private static final char BOM = '\uFEFF';

    private final int maxLines;

    public CsvLogParserImpl(AnalysisProperties properties) {
        this.maxLines = properties.maxLines();
    }

    @Override
    public ParseStatistics parse(InputStream inputStream, Consumer<AccessLog> logConsumer) {
        int totalLines = 0;
        int successCount = 0;
        int errorCount = 0;
        List<ParseErrorSample> errorSamples = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String firstLine = reader.readLine(); // 첫 줄 읽음
            if (firstLine == null) {
                return new ParseStatistics(0, 0, 0, List.of()); // Empty file
            }

            // BOM 제거
            if (!firstLine.isEmpty() && firstLine.charAt(0) == BOM) { // 첫줄 BOM
                firstLine = firstLine.substring(1); // -> ""
            }

            // 첫 줄이 빈 줄이면 다음 줄(헤더)을 읽음
            if (firstLine.isBlank()) {
                String headerLine = reader.readLine(); // 1줄 더 읽어서 header 읽음
                if (headerLine == null) {
                    return new ParseStatistics(0, 0, 0, List.of()); // header가 없으면 바로 종료
                }
            } else if (isHeaderLine(firstLine)) {
                // 첫 줄이 바로 헤더인 경우 스킵
            } else {
                // 처음부터 데이터로 시작하는 경우
                totalLines++;
                try {
                    AccessLog accessLog = parseLine(firstLine);
                    logConsumer.accept(accessLog);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    collectErrorSample(errorSamples, totalLines, firstLine, e.getMessage());
                }
            }

            // 데이터 라인 처리
            String line;
            while ((line = reader.readLine()) != null) { // 다음 줄 부터 읽기 시작, 정상 포맷 파일이면 row 3
                if (totalLines >= maxLines) {
                    log.warn("최대 라인 수({}) 도달, 파싱 중단", maxLines);
                    break;
                }

                if (line.isBlank()) {
                    continue;
                }

                totalLines++;
                try {
                    AccessLog accessLog = parseLine(line);
                    logConsumer.accept(accessLog);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    collectErrorSample(errorSamples, totalLines, line, e.getMessage());
                }
            }

        } catch (IOException e) {
            log.error("CSV 파일 읽기 실패", e);
            throw new RuntimeException("CSV 파일 읽기 실패", e);
        }

        log.info("CSV 파싱 완료: 총 {}줄, 성공 {}, 오류 {}", totalLines, successCount, errorCount);
        return new ParseStatistics(totalLines, successCount, errorCount, List.copyOf(errorSamples));
    }

    private boolean isHeaderLine(String line) {
        return line.startsWith("TimeGenerated");
    }

    /**
     * CSV 한 줄을 상태 머신으로 파싱하여 필드 목록을 추출한 뒤 AccessLog로 변환한다.
     * RFC 4180: 따옴표 내 쉼표와 이스케이프된 따옴표({@code ""})를 처리.
     */
    AccessLog parseLine(String line) {
        List<String> fields = splitCsvLine(line);

        if (fields.size() != EXPECTED_COLUMNS) {
            throw new IllegalArgumentException(
                    "컬럼 수 불일치: expected=" + EXPECTED_COLUMNS + ", actual=" + fields.size());
        }

        try {
            return new AccessLog(
                    fields.get(0),                          // timeGenerated
                    fields.get(1),                          // clientIp
                    fields.get(2),                          // httpMethod
                    fields.get(3),                          // requestUri
                    fields.get(4),                          // userAgent
                    Integer.parseInt(fields.get(5)),        // httpStatus
                    fields.get(6),                          // httpVersion
                    Long.parseLong(fields.get(7)),          // receivedBytes
                    Long.parseLong(fields.get(8)),          // sentBytes
                    Double.parseDouble(fields.get(9)),      // clientResponseTime
                    fields.get(10),                         // sslProtocol
                    fields.get(11)                          // originalRequestUriWithArgs
            );
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("숫자 변환 실패: " + e.getMessage());
        }
    }

    /**
     * 상태 머신 기반 CSV 라인 분할.
     * 따옴표 내 쉼표를 필드 구분자로 취급하지 않고, {@code ""}를 {@code "}로 치환.
     *
     * <p>예시:
     * <pre>{@code
     * "1/29/2026, 5:44:10.000 AM",112.144.4.88,GET,/assets/Dormancy.css,"Mozilla/5.0 (Windows NT 10.0; Win64; x64)",200,HTTP/1.1,2594,2653,0,TLSv1.3,/assets/Dormancy.css
     * }</pre>
     */
    List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // 다음 글자 접근 가능 && 다음 문자도 따옴표면 이스케이프 ("" → ")
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }

        fields.add(current.toString());
        return fields;
    }

    private void collectErrorSample(List<ParseErrorSample> samples, int lineNumber,
                                    String line, String reason) {
        if (samples.size() < MAX_ERROR_SAMPLES) {
            // 샘플에 저장할 라인은 200자로 제한
            String truncated = line.length() > 200 ? line.substring(0, 200) + "..." : line;
            samples.add(new ParseErrorSample(lineNumber, truncated, reason));
        }
    }
}
