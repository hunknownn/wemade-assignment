package com.example.wemadeassignment.client;

import com.example.wemadeassignment.config.IpInfoProperties;
import com.example.wemadeassignment.domain.IpInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class IpInfoApiClient implements IpInfoClient {

    private static final Logger log = LoggerFactory.getLogger(IpInfoApiClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String token;

    public IpInfoApiClient(RestTemplate restTemplate, IpInfoProperties properties) {
        this.restTemplate = restTemplate;
        this.baseUrl = properties.baseUrl();
        this.token = properties.token();
    }

    @Override
    public IpInfo fetch(String ip) {
        String url = baseUrl + "/" + ip;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        log.debug("ipinfo 조회: {}", ip);
        IpInfo body = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), IpInfo.class)
                .getBody();
        if (body == null) {
            throw new IllegalStateException("ipinfo API returned empty body for ip=" + ip);
        }
        return body;
    }
}
