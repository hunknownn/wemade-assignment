package com.example.wemadeassignment.service;

import com.example.wemadeassignment.domain.IpInfo;

import java.util.List;

/** 상위 N개 IP에 대해 ipinfo 정보를 조회하는 서비스 */
public interface IpEnrichmentService {

    List<IpInfo> enrich(List<String> ips);
}
