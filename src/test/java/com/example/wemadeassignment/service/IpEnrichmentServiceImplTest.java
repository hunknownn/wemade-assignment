package com.example.wemadeassignment.service;

import com.example.wemadeassignment.client.IpInfoClient;
import com.example.wemadeassignment.config.IpInfoProperties;
import com.example.wemadeassignment.domain.IpInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpEnrichmentServiceImplTest {

    @Mock
    private IpInfoClient ipInfoClient;

    private Cache<String, IpInfo> cache;
    private IpEnrichmentServiceImpl service;

    private static final IpInfo SAMPLE = new IpInfo(
            "8.8.8.8", "AS15169", "Google LLC", "google.com",
            "US", "United States", "NA", "North America"
    );

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder().maximumSize(100).build();
        IpInfoProperties properties = new IpInfoProperties(
                "test-token", "https://api.ipinfo.io/lite", 3000, 2,
                new IpInfoProperties.CacheProperties(100, 3600)
        );
        service = new IpEnrichmentServiceImpl(ipInfoClient, cache, Executors.newFixedThreadPool(2), properties);
    }

    @Test
    @DisplayName("정상 조회 시 API 호출 후 캐시에 저장")
    void fetchAndCache() {
        when(ipInfoClient.fetch("8.8.8.8")).thenReturn(SAMPLE);

        List<IpInfo> results = service.enrich(List.of("8.8.8.8"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).asName()).isEqualTo("Google LLC");
        assertThat(cache.getIfPresent("8.8.8.8")).isNotNull();
    }

    @Test
    @DisplayName("캐시 히트 시 API 미호출")
    void cacheHit() {
        cache.put("8.8.8.8", SAMPLE);

        List<IpInfo> results = service.enrich(List.of("8.8.8.8"));

        assertThat(results.get(0).asName()).isEqualTo("Google LLC");
        verify(ipInfoClient, never()).fetch(any());
    }

    @Test
    @DisplayName("API 실패 후 재시도하여 성공")
    void retryThenSuccess() {
        when(ipInfoClient.fetch("8.8.8.8"))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(SAMPLE);

        List<IpInfo> results = service.enrich(List.of("8.8.8.8"));

        assertThat(results.get(0).asName()).isEqualTo("Google LLC");
        verify(ipInfoClient, times(2)).fetch("8.8.8.8");
    }

    @Test
    @DisplayName("최대 재시도 초과 시 UNKNOWN fallback")
    void retryExhausted() {
        when(ipInfoClient.fetch("8.8.8.8"))
                .thenThrow(new RuntimeException("timeout"));

        List<IpInfo> results = service.enrich(List.of("8.8.8.8"));

        assertThat(results.get(0).country()).isEqualTo("UNKNOWN");
        verify(ipInfoClient, times(3)).fetch("8.8.8.8"); // 1 + 2 retries
    }

    @Test
    @DisplayName("429 응답 시 재시도 없이 즉시 UNKNOWN fallback")
    void rateLimitNoRetry() {
        when(ipInfoClient.fetch("8.8.8.8"))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        List<IpInfo> results = service.enrich(List.of("8.8.8.8"));

        assertThat(results.get(0).country()).isEqualTo("UNKNOWN");
        verify(ipInfoClient, times(1)).fetch("8.8.8.8");
    }

    @Test
    @DisplayName("HTTP 500 에러 후 재시도하여 성공")
    void httpServerErrorRetryThenSuccess() {
        when(ipInfoClient.fetch("8.8.8.8"))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .thenReturn(SAMPLE);

        List<IpInfo> results = service.enrich(List.of("8.8.8.8"));

        assertThat(results.get(0).asName()).isEqualTo("Google LLC");
        verify(ipInfoClient, times(2)).fetch("8.8.8.8");
    }

    @Test
    @DisplayName("조회 실패(UNKNOWN) 결과는 캐시하지 않음")
    void unknownResultNotCached() {
        when(ipInfoClient.fetch("8.8.8.8"))
                .thenThrow(new RuntimeException("timeout"));

        service.enrich(List.of("8.8.8.8"));

        assertThat(cache.getIfPresent("8.8.8.8")).isNull();
    }

    @Test
    @DisplayName("여러 IP 병렬 조회")
    void enrichMultipleIps() {
        IpInfo info1 = new IpInfo("1.1.1.1", "AS13335", "Cloudflare", "cloudflare.com",
                "US", "United States", "NA", "North America");
        IpInfo info2 = new IpInfo("8.8.8.8", "AS15169", "Google LLC", "google.com",
                "US", "United States", "NA", "North America");

        when(ipInfoClient.fetch("1.1.1.1")).thenReturn(info1);
        when(ipInfoClient.fetch("8.8.8.8")).thenReturn(info2);

        List<IpInfo> results = service.enrich(List.of("1.1.1.1", "8.8.8.8"));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(IpInfo::asName)
                .containsExactlyInAnyOrder("Cloudflare", "Google LLC");
    }

    @Test
    @DisplayName("빈 리스트 입력 시 빈 결과 반환")
    void enrichEmptyList() {
        List<IpInfo> results = service.enrich(List.of());

        assertThat(results).isEmpty();
        verify(ipInfoClient, never()).fetch(any());
    }

    @Test
    @DisplayName("병렬 실행 검증 — 동시에 2개 이상 실행")
    void enrichRunsInParallel() {
        AtomicInteger concurrent = new AtomicInteger(0);
        AtomicInteger peakConcurrent = new AtomicInteger(0);

        when(ipInfoClient.fetch(any())).thenAnswer(invocation -> {
            int current = concurrent.incrementAndGet();
            peakConcurrent.updateAndGet(peak -> Math.max(peak, current));
            Thread.sleep(200);
            concurrent.decrementAndGet();
            return SAMPLE;
        });

        service.enrich(List.of("1.1.1.1", "2.2.2.2", "3.3.3.3"));

        assertThat(peakConcurrent.get()).isGreaterThan(1);
    }
}
