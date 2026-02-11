package com.example.wemadeassignment.service;

import com.example.wemadeassignment.domain.AccessLog;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CSV 파싱 콜백으로 사용되는 스트리밍 집계기.
 * 파싱된 로그 한 건씩 받아서 IP/Path/StatusCode별 카운트를 누적한다.
 */
public class LogAggregator {

    private long totalRequests;
    private final Map<String, Long> ipCounts = new HashMap<>();
    private final Map<String, Long> pathCounts = new HashMap<>();
    private final Map<Integer, Long> statusCodeCounts = new HashMap<>();

    public void aggregate(AccessLog log) {
        totalRequests++;
        ipCounts.merge(log.clientIp(), 1L, Long::sum);
        pathCounts.merge(log.requestUri(), 1L, Long::sum);
        statusCodeCounts.merge(log.httpStatus(), 1L, Long::sum);
    }

    /** 상위 N개를 요청 수 내림차순으로 추출 */
    public <K> Map<K, Long> getTopN(Map<K, Long> map, int n) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue().reversed())
                .limit(n)
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll);
    }

    /** 상태 코드 그룹별 비율 (2xx, 3xx, 4xx, 5xx) */
    public Map<String, Double> getStatusGroupRatios() {
        Map<String, Double> ratios = new LinkedHashMap<>();
        ratios.put("2xx", calcRatio(200, 299));
        ratios.put("3xx", calcRatio(300, 399));
        ratios.put("4xx", calcRatio(400, 499));
        ratios.put("5xx", calcRatio(500, 599));
        return ratios;
    }

    private double calcRatio(int from, int to) {
        if (totalRequests == 0) return 0.0;
        long count = statusCodeCounts.entrySet().stream()
                .filter(e -> e.getKey() >= from && e.getKey() <= to)
                .mapToLong(Map.Entry::getValue)
                .sum();
        return Math.round((double) count / totalRequests * 10000) / 10000.0;
    }

    public long getTotalRequests() { return totalRequests; }
    public Map<String, Long> getIpCounts() { return ipCounts; }
    public Map<String, Long> getPathCounts() { return pathCounts; }
    public Map<Integer, Long> getStatusCodeCounts() { return statusCodeCounts; }
}
