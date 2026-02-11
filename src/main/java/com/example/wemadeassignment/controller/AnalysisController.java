package com.example.wemadeassignment.controller;

import com.example.wemadeassignment.domain.AnalysisResult;
import com.example.wemadeassignment.dto.AnalysisResponse;
import com.example.wemadeassignment.dto.AnalysisSubmitResponse;
import com.example.wemadeassignment.dto.ErrorResponse;
import com.example.wemadeassignment.exception.AnalysisNotFoundException;
import com.example.wemadeassignment.service.AnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Pattern;

@Tag(name = "Analysis", description = "CSV 접속 로그 분석 API")
@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisController {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(summary = "분석 요청 제출", description = "CSV 접속 로그 파일을 업로드하여 비동기 분석을 시작한다.")
    @ApiResponse(responseCode = "202", description = "분석 요청 접수 완료")
    @ApiResponse(responseCode = "400", description = "잘못된 요청 (빈 파일, 크기 초과 등)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisSubmitResponse> submit(@RequestParam("file") MultipartFile file) {
        AnalysisResult result = analysisService.submitAnalysis(file);
        AnalysisSubmitResponse response = AnalysisSubmitResponse.of(
                result.getAnalysisId(), result.getStatus().name());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "분석 결과 조회", description = "analysisId로 분석 결과를 조회한다. PROCESSING 상태이면 집계 필드는 null이다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "분석 결과를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping("/{analysisId}")
    public ResponseEntity<AnalysisResponse> getResult(@PathVariable String analysisId) {
        if (!UUID_PATTERN.matcher(analysisId).matches()) {
            throw new IllegalArgumentException("잘못된 분석 ID 형식입니다.");
        }
        AnalysisResult result = analysisService.getAnalysis(analysisId);
        if (result == null) {
            throw new AnalysisNotFoundException(analysisId);
        }
        return ResponseEntity.ok(AnalysisResponse.from(result));
    }
}
