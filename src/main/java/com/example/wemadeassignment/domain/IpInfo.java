package com.example.wemadeassignment.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "IP 상세 정보 (ipinfo 조회 결과)")
public record IpInfo(
        @Schema(description = "IP 주소", example = "8.8.8.8")
        String ip,

        @Schema(description = "ASN 번호", example = "AS15169")
        String asn,

        @Schema(description = "AS 이름", example = "Google LLC")
        @JsonProperty("as_name") String asName,

        @Schema(description = "AS 도메인", example = "google.com")
        @JsonProperty("as_domain") String asDomain,

        @Schema(description = "국가 코드 (ISO 3166-1 alpha-2)", example = "US")
        @JsonProperty("country_code") String countryCode,

        @Schema(description = "국가명", example = "United States")
        String country,

        @Schema(description = "대륙 코드", example = "NA")
        @JsonProperty("continent_code") String continentCode,

        @Schema(description = "대륙명", example = "North America")
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
