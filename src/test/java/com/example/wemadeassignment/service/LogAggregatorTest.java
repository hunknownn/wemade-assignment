package com.example.wemadeassignment.service;

import com.example.wemadeassignment.domain.AccessLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogAggregatorTest {

    private LogAggregator aggregator;

    private static AccessLog log(String ip, String path, int status) {
        return new AccessLog("2024-01-01T00:00:00", ip, "GET", path,
                "Mozilla/5.0", status, "HTTP/1.1", 100, 200, 0.5, "TLSv1.2", path);
    }

    @BeforeEach
    void setUp() {
        aggregator = new LogAggregator();
    }

    @Test
    @DisplayName("단건 집계")
    void aggregateSingle() {
        aggregator.aggregate(log("1.1.1.1", "/api/test", 200));

        assertThat(aggregator.getTotalRequests()).isEqualTo(1);
        assertThat(aggregator.getIpCounts()).containsEntry("1.1.1.1", 1L);
        assertThat(aggregator.getPathCounts()).containsEntry("/api/test", 1L);
        assertThat(aggregator.getStatusCodeCounts()).containsEntry(200, 1L);
    }

    @Test
    @DisplayName("동일 IP/Path 여러 건 카운트 누적")
    void aggregateMultipleSameKey() {
        aggregator.aggregate(log("1.1.1.1", "/api/test", 200));
        aggregator.aggregate(log("1.1.1.1", "/api/test", 200));
        aggregator.aggregate(log("1.1.1.1", "/api/test", 404));

        assertThat(aggregator.getTotalRequests()).isEqualTo(3);
        assertThat(aggregator.getIpCounts().get("1.1.1.1")).isEqualTo(3);
        assertThat(aggregator.getPathCounts().get("/api/test")).isEqualTo(3);
        assertThat(aggregator.getStatusCodeCounts()).containsEntry(200, 2L);
        assertThat(aggregator.getStatusCodeCounts()).containsEntry(404, 1L);
    }

    @Test
    @DisplayName("getTopN — 상위 N개만 내림차순 추출")
    void getTopN() {
        aggregator.aggregate(log("1.1.1.1", "/a", 200));
        aggregator.aggregate(log("2.2.2.2", "/b", 200));
        aggregator.aggregate(log("2.2.2.2", "/b", 200));
        aggregator.aggregate(log("3.3.3.3", "/c", 200));
        aggregator.aggregate(log("3.3.3.3", "/c", 200));
        aggregator.aggregate(log("3.3.3.3", "/c", 200));

        Map<String, Long> top2 = aggregator.getTopN(aggregator.getIpCounts(), 2);

        assertThat(top2).hasSize(2);
        assertThat(top2.keySet().stream().toList()).containsExactly("3.3.3.3", "2.2.2.2");
    }

    @Test
    @DisplayName("getTopN — N이 전체 크기보다 클 때 전부 반환")
    void getTopNLargerThanSize() {
        aggregator.aggregate(log("1.1.1.1", "/a", 200));
        aggregator.aggregate(log("2.2.2.2", "/b", 200));

        Map<String, Long> top10 = aggregator.getTopN(aggregator.getIpCounts(), 10);

        assertThat(top10).hasSize(2);
    }

    @Test
    @DisplayName("상태 코드 그룹 비율 계산")
    void statusGroupRatios() {
        // 2xx: 7건, 3xx: 1건, 4xx: 1건, 5xx: 1건 = 총 10건
        for (int i = 0; i < 7; i++) aggregator.aggregate(log("1.1.1.1", "/a", 200));
        aggregator.aggregate(log("1.1.1.1", "/a", 301));
        aggregator.aggregate(log("1.1.1.1", "/a", 404));
        aggregator.aggregate(log("1.1.1.1", "/a", 500));

        Map<String, Double> ratios = aggregator.getStatusGroupRatios();

        assertThat(ratios.get("2xx")).isEqualTo(0.7);
        assertThat(ratios.get("3xx")).isEqualTo(0.1);
        assertThat(ratios.get("4xx")).isEqualTo(0.1);
        assertThat(ratios.get("5xx")).isEqualTo(0.1);
    }

    @Test
    @DisplayName("요청 0건일 때 비율 전부 0.0")
    void statusGroupRatiosEmpty() {
        Map<String, Double> ratios = aggregator.getStatusGroupRatios();

        assertThat(ratios.values()).allMatch(v -> v == 0.0);
    }

    @Test
    @DisplayName("다양한 상태 코드가 올바른 그룹에 집계")
    void statusGroupVariousCodes() {
        aggregator.aggregate(log("1.1.1.1", "/a", 200));
        aggregator.aggregate(log("1.1.1.1", "/a", 201));
        aggregator.aggregate(log("1.1.1.1", "/a", 204));
        aggregator.aggregate(log("1.1.1.1", "/a", 499));

        Map<String, Double> ratios = aggregator.getStatusGroupRatios();

        assertThat(ratios.get("2xx")).isEqualTo(0.75);
        assertThat(ratios.get("4xx")).isEqualTo(0.25);
        assertThat(ratios.get("3xx")).isEqualTo(0.0);
        assertThat(ratios.get("5xx")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getTopN — N=0이면 빈 Map 반환")
    void getTopNZero() {
        aggregator.aggregate(log("1.1.1.1", "/a", 200));

        Map<String, Long> top0 = aggregator.getTopN(aggregator.getIpCounts(), 0);

        assertThat(top0).isEmpty();
    }

    @Test
    @DisplayName("getTopN — 빈 Map에서 호출 시 빈 Map 반환")
    void getTopNEmptyMap() {
        Map<String, Long> result = aggregator.getTopN(aggregator.getIpCounts(), 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("상태 코드 그룹 경계값 — 199, 299, 300, 599, 600은 올바른 그룹에 분류")
    void statusGroupBoundaryValues() {
        aggregator.aggregate(log("1.1.1.1", "/a", 199));  // 어디에도 안 속함
        aggregator.aggregate(log("1.1.1.1", "/a", 299));  // 2xx
        aggregator.aggregate(log("1.1.1.1", "/a", 300));  // 3xx
        aggregator.aggregate(log("1.1.1.1", "/a", 599));  // 5xx
        aggregator.aggregate(log("1.1.1.1", "/a", 600));  // 어디에도 안 속함

        Map<String, Double> ratios = aggregator.getStatusGroupRatios();

        assertThat(ratios.get("2xx")).isEqualTo(0.2);   // 299 → 1/5
        assertThat(ratios.get("3xx")).isEqualTo(0.2);   // 300 → 1/5
        assertThat(ratios.get("4xx")).isEqualTo(0.0);
        assertThat(ratios.get("5xx")).isEqualTo(0.2);   // 599 → 1/5
        // 199, 600은 어느 그룹에도 속하지 않으므로 합계 0.6 (1.0이 아님)
    }

    @Test
    @DisplayName("getTopN — 동일 카운트일 때 N개 반환 보장")
    void getTopNSameCount() {
        aggregator.aggregate(log("1.1.1.1", "/a", 200));
        aggregator.aggregate(log("2.2.2.2", "/b", 200));
        aggregator.aggregate(log("3.3.3.3", "/c", 200));

        Map<String, Long> top2 = aggregator.getTopN(aggregator.getIpCounts(), 2);

        assertThat(top2).hasSize(2);
        assertThat(top2.values()).allMatch(v -> v == 1L);
    }
}
