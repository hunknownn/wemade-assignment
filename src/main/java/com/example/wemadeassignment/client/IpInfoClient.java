package com.example.wemadeassignment.client;

import com.example.wemadeassignment.domain.IpInfo;

/** ipinfo Lite API 호출 클라이언트 */
public interface IpInfoClient {

    IpInfo fetch(String ip);
}
