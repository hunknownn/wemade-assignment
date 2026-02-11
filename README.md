# 접속 로그 분석 API

CSV 접속 로그 파일을 업로드하면 파싱/집계 후 [ipinfo Lite API](https://ipinfo.io/developers/data-types#lite-ip-data-api)로 상위 IP 정보를 조회하여 통계 리포트를 제공하는 Spring Boot 애플리케이션입니다.

## 기술 스택

- Java 21, Spring Boot 3.5.10
- Caffeine Cache (IP 조회 결과 캐싱)
- springdoc-openapi (Swagger UI)
- Lombok, JUnit 5

## 실행 방법

### 사전 준비

- JDK 21+
- [ipinfo](https://ipinfo.io/) 계정 및 API 토큰

### 실행

```bash
# 1. 환경변수 설정
export IPINFO_TOKEN=your-token-here

# 2. 빌드 및 실행
./gradlew bootRun

# 3. Swagger UI 접속
# http://localhost:8080/swagger-ui.html
```

### 테스트

```bash
./gradlew test
```

## API 엔드포인트

### POST /api/v1/analysis — 분석 요청 제출

CSV 파일을 업로드하여 비동기 분석을 시작합니다. 즉시 `202 Accepted`를 반환하며, 분석은 백그라운드에서 진행됩니다.

```bash
curl -X POST http://localhost:8080/api/v1/analysis \
  -F "file=@access-log.csv"
```

**응답 (202)**
```json
{
  "analysisId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PROCESSING",
  "message": "분석 요청이 접수되었습니다."
}
```

### GET /api/v1/analysis/{analysisId} — 분석 결과 조회

`PROCESSING` 상태이면 집계 필드는 null로 반환됩니다. 분석이 완료되면 `COMPLETED` 상태와 함께 전체 통계가 포함됩니다.

```bash
curl http://localhost:8080/api/v1/analysis/550e8400-e29b-41d4-a716-446655440000
```

**응답 (200) — COMPLETED**
```json
{
  "analysisId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "totalRequests": 15234,
  "statusCodeCounts": {
    "200": 12000,
    "404": 500,
    "302": 400,
    "500": 34
  },
  "statusGroupRatios": {
    "2xx": 0.788,
    "3xx": 0.026,
    "4xx": 0.184,
    "5xx": 0.002
  },
  "topPaths": {
    "/api/users": 3200,
    "/api/login": 2100
  },
  "topIps": {
    "121.158.115.86": 450,
    "8.8.8.8": 320
  },
  "ipDetails": [
    {
      "ip": "121.158.115.86",
      "asn": "AS9318",
      "as_name": "SK Broadband Co Ltd",
      "as_domain": "skbroadband.com",
      "country_code": "KR",
      "country": "South Korea",
      "continent_code": "AS",
      "continent": "Asia"
    }
  ],
  "parseErrorCount": 2,
  "parseErrorSamples": [
    {
      "lineNumber": 42,
      "line": "invalid,,line,data",
      "reason": "컬럼 수 불일치: expected=12, actual=4"
    }
  ],
  "createdAt": "2025-01-15T10:30:00",
  "completedAt": "2025-01-15T10:30:05",
  "failureReason": null
}
```

## 프로젝트 구조

```
com.example.wemadeassignment/
├── controller/         AnalysisController — REST API 엔드포인트
├── service/            AnalysisService, IpEnrichmentService, LogAggregator
├── parser/             CsvLogParser — RFC 4180 호환 상태 머신 파서
├── client/             IpInfoApiClient — ipinfo Lite API 호출
├── repository/         InMemoryAnalysisRepository — ConcurrentHashMap 저장소
├── domain/             AccessLog, AnalysisResult, IpInfo, AnalysisStatus
├── dto/                AnalysisResponse, AnalysisSubmitResponse, ErrorResponse
├── config/             AppConfig, AnalysisProperties, IpInfoProperties
└── exception/          GlobalExceptionHandler, 커스텀 예외 클래스
```

각 계층은 인터페이스 → 구현체 패턴을 따르며, 서비스와 클라이언트를 인터페이스로 분리하여 테스트 시 Mock 주입이 용이하도록 설계했습니다.

## 설계 요약

### 아키텍처

```
Controller → Service → Parser   (CSV 파싱)
                     → Aggregator (스트리밍 집계)
                     → IpEnrichmentService → IpInfoApiClient (IP 조회)
                                           → Caffeine Cache   (캐시)
```

계층을 `controller / service / parser / client / repository`로 분리하고, 각 계층은 인터페이스 → 구현체 패턴을 따릅니다.

### CSV 파서: 상태 머신 기반 직접 구현

실제 로그에서 `UserAgent`와 `TimeGenerated` 필드에 쌍따옴표 + 내부 쉼표가 존재하기 때문에 단순 `split(",")`으로는 처리할 수 없었습니다.

- **상태 머신**: `NORMAL` / `IN_QUOTES` 상태를 전환하며 필드를 분리
- **스트리밍**: `BufferedReader`로 라인 단위 읽기, 파일 전체를 메모리에 올리지 않음
- **콜백 패턴**: `Consumer<AccessLog>`를 받아 파싱 즉시 집계기로 전달

### 비동기 처리

`Executor.execute()`로 분석을 백그라운드 스레드에서 실행하고, POST 요청은 즉시 202를 반환합니다.

- `MultipartFile`은 요청 종료 시 해제되므로 임시 파일로 저장 후 async 메서드에 경로 전달
- 스레드 풀 포화 시 `ServerBusyException` → 503 응답

### IP Enrichment: Caffeine 캐시 + 선형 백오프 재시도

상위 N개 IP에 대해 ipinfo Lite API를 `CompletableFuture`로 병렬 호출합니다.

- **Caffeine 캐시**: 최대 10,000건, 1시간 TTL로 동일 IP 반복 조회 방지
- **재시도**: 최대 2회, 선형 백오프 (`100ms × attempt`)
- **429 (Rate Limit)**: 재시도 없이 즉시 fallback
- **Fallback**: 모든 실패 시 `IpInfo.unknown()` 반환

### 인메모리 저장소

`ConcurrentHashMap` 기반 저장소로 RDB/Redis 없이 분석 결과를 보관합니다. `AnalysisResult`의 `status` 필드는 `volatile`로 선언하여 비동기 스레드 간 가시성을 보장합니다.

## 가장 중요하다고 판단한 기능

**CSV 파서의 정확성과 스트리밍 처리**가 가장 중요하다고 판단했습니다.

이 애플리케이션의 핵심은 CSV 로그를 정확하게 파싱하여 집계하는 것입니다. 실제 로그 데이터에는 쌍따옴표 안에 쉼표가 포함된 필드(UserAgent 등)가 존재하므로, 단순 문자열 분할이 아닌 RFC 4180 호환 상태 머신 파서가 필요했습니다. 동시에 50MB/200,000 라인 규모의 파일을 메모리 폭주 없이 처리하기 위해 콜백 기반 스트리밍 구조로 설계했습니다.

## 특히 신경 쓴 부분

- **전 구간 스트리밍**: 파일 업로드 → 임시 파일 저장 → `BufferedReader` 라인별 읽기 → 콜백으로 즉시 집계. 어느 단계에서도 전체 데이터가 메모리에 올라가지 않습니다.
- **외부 API 장애 대응**: ipinfo 호출 실패 시 재시도(선형 백오프) → 429는 즉시 fallback → 최종 실패 시 UNKNOWN 반환. 분석 전체가 실패하지 않고 가능한 범위까지 결과를 제공합니다.
- **비동기 처리의 안전성**: `MultipartFile`의 생명주기를 고려하여 임시 파일로 복사 후 async 전달, `finally`에서 임시 파일 삭제, 스레드 풀 포화 시 503 응답으로 시스템을 보호합니다.

## 테스트 전략

### 테스트 구성

| 레이어 | 테스트 클래스 | 방식 |
|-------|------------|------|
| Parser | `CsvLogParserImplTest` | 단위 테스트 (외부 의존 없음) |
| Aggregator | `LogAggregatorTest` | 단위 테스트 (외부 의존 없음) |
| Service | `AnalysisServiceImplTest` | Mock (Parser, IpEnrichment) |
| Service | `IpEnrichmentServiceImplTest` | Mock (IpInfoClient) |
| Controller | `AnalysisControllerTest` | MockMvc + MockitoBean |
| 통합 | `AnalysisServiceIntegrationTest` | 실제 Parser + Stub IpEnrichment |

### 외부 API 격리

ipinfo API는 `IpInfoClient` 인터페이스로 추상화하여, 테스트에서는 Mockito Mock으로 대체합니다. 통합 테스트에서는 고정값을 반환하는 Stub 구현체를 사용하여 네트워크 의존 없이 전체 파이프라인을 검증합니다.

### 주요 엣지 케이스 커버리지

- **CSV 파서**: BOM 처리, 따옴표 내 쉼표/이스케이프, 빈 파일, 빈 줄, 빈 필드(SslProtocol), maxLines 제한, 숫자 변환 오류
- **IP Enrichment**: 캐시 히트, 재시도 후 성공, 재시도 소진 → UNKNOWN, 429 즉시 fallback, 병렬 실행 검증, UNKNOWN 미캐싱
- **컨트롤러**: 잘못된 UUID, 파일 누락, 로그 인젝션 시도, 503 응답

## 실 서비스 운영 시 개선 포인트

- **저장소 도입**: 현재 인메모리 보관으로 서버 재시작 시 결과가 유실됩니다. 실 서비스에서는 RDB나 Redis를 도입하여 분석 결과를 영속화하고, 대용량 결과는 Object Storage에 저장하는 방식을 고려할 수 있습니다.
- **분산 처리**: 단일 서버의 스레드 풀로는 동시 분석 요청 수에 한계가 있습니다. 메시지 큐(Kafka, RabbitMQ)를 통한 작업 분배와 워커 스케일아웃으로 처리량을 확장할 수 있습니다.
- **모니터링 및 오토스케일링**: Micrometer + Prometheus로 메트릭을 수집하되, 부하 메트릭(처리 시간, 503 빈도)은 K8s HPA에 연동하여 자동 스케일아웃하고, 외부 의존성 메트릭(ipinfo 429 빈도, 캐시 히트율)은 운영자 알림으로 분리하여 대응할 수 있습니다.
