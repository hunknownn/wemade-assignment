package com.example.wemadeassignment.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IpInfo(
        String ip,
        String asn,
        @JsonProperty("as_name") String asName,
        @JsonProperty("as_domain") String asDomain,
        @JsonProperty("country_code") String countryCode,
        String country,
        @JsonProperty("continent_code") String continentCode,
        String continent
) {
    private static final String UNKNOWN = "UNKNOWN";

    /** API 호출 실패 시 반환할 기본값 */
    public static IpInfo unknown(String ip) {
        return new IpInfo(ip, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);
    }

    public boolean isUnknown() {
        return UNKNOWN.equals(country);
    }
}
