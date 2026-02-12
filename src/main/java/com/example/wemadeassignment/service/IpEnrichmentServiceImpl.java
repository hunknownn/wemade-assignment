package com.example.wemadeassignment.service;

import com.example.wemadeassignment.client.IpInfoClient;
import com.example.wemadeassignment.config.IpInfoProperties;
import com.example.wemadeassignment.domain.IpInfo;
import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class IpEnrichmentServiceImpl implements IpEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(IpEnrichmentServiceImpl.class);

    private final IpInfoClient ipInfoClient;
    private final Cache<String, IpInfo> cache;
    private final Executor executor;
    private final int maxRetries;

    public IpEnrichmentServiceImpl(IpInfoClient ipInfoClient,
                                   Cache<String, IpInfo> cache,
                                   @Qualifier("ipEnrichmentExecutor") Executor executor,
                                   IpInfoProperties properties) {
        this.ipInfoClient = ipInfoClient;
        this.cache = cache;
        this.executor = executor;
        this.maxRetries = properties.maxRetries();
    }

    @Override
    public List<IpInfo> enrich(List<String> ips) {
        List<CompletableFuture<IpInfo>> futures = ips.stream()
                .map(ip -> CompletableFuture.supplyAsync(() -> lookup(ip), executor))
                .toList();

        List<IpInfo> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long cacheHits = results.stream().filter(r -> !r.isUnknown()).count();
        log.info("IP 조회 완료: 전체={}건, 성공={}건, 실패={}건",
                ips.size(), cacheHits, ips.size() - cacheHits);

        return results;
    }

    private IpInfo lookup(String ip) {
        IpInfo cached = cache.getIfPresent(ip);
        if (cached != null) {
            log.debug("캐시 히트: ip={}", ip);
            return cached;
        }

        log.debug("캐시 미스, API 조회: ip={}", ip);
        IpInfo result = fetchWithRetry(ip);
        if (!result.isUnknown()) {
            cache.put(ip, result);
        }
        return result;
    }

    private IpInfo fetchWithRetry(String ip) {
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                return ipInfoClient.fetch(ip);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn("ipinfo 429 rate limit: ip={}", ip);
                    return IpInfo.unknown(ip);
                }
                if (attempt > maxRetries) {
                    log.error("ipinfo 조회 최종 실패: ip={}, status={}", ip, e.getStatusCode(), e);
                    return IpInfo.unknown(ip);
                }
                log.warn("ipinfo 조회 실패, 재시도 {}/{}: ip={}", attempt, maxRetries, ip);
                sleep(100L * attempt);
            } catch (Exception e) {
                if (attempt > maxRetries) {
                    log.error("ipinfo 조회 최종 실패: ip={}, attempts={}", ip, attempt, e);
                    return IpInfo.unknown(ip);
                }
                log.warn("ipinfo 조회 실패, 재시도 {}/{}: ip={}", attempt, maxRetries, ip);
                sleep(100L * attempt);
            }
        }
        return IpInfo.unknown(ip);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
