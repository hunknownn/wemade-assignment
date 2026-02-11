package com.example.wemadeassignment.controller;

import com.example.wemadeassignment.domain.AnalysisResult;
import com.example.wemadeassignment.domain.AnalysisStatus;
import com.example.wemadeassignment.exception.AnalysisNotFoundException;
import com.example.wemadeassignment.exception.ServerBusyException;
import com.example.wemadeassignment.service.AnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    private static final String BASE_URL = "/api/v1/analysis";
    private static final String VALID_UUID = "550e8400-e29b-41d4-a716-446655440000";

    // === POST /api/v1/analysis ===

    @Test
    @DisplayName("POST 정상 업로드 → 202")
    void submitReturns202() throws Exception {
        AnalysisResult result = new AnalysisResult(VALID_UUID);
        when(analysisService.submitAnalysis(any())).thenReturn(result);

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "data".getBytes());

        mockMvc.perform(multipart(BASE_URL).file(file))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.analysisId").value(VALID_UUID))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("POST 빈 파일 → 400")
    void submitEmptyFileReturns400() throws Exception {
        when(analysisService.submitAnalysis(any()))
                .thenThrow(new IllegalArgumentException("파일이 비어있습니다."));

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart(BASE_URL).file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("파일이 비어있습니다."));
    }

    @Test
    @DisplayName("POST file 파라미터 누락 → 400")
    void submitMissingFileReturns400() throws Exception {
        mockMvc.perform(multipart(BASE_URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("필수 파라미터 'file'이 누락되었습니다."));
    }

    @Test
    @DisplayName("POST 서버 바쁨 → 503")
    void submitServerBusyReturns503() throws Exception {
        when(analysisService.submitAnalysis(any()))
                .thenThrow(new ServerBusyException());

        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "data".getBytes());

        mockMvc.perform(multipart(BASE_URL).file(file))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    // === GET /api/v1/analysis/{analysisId} ===

    @Test
    @DisplayName("GET 정상 조회 → 200")
    void getResultReturns200() throws Exception {
        AnalysisResult result = new AnalysisResult(VALID_UUID);
        when(analysisService.getAnalysis(eq(VALID_UUID))).thenReturn(result);

        mockMvc.perform(get(BASE_URL + "/" + VALID_UUID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(VALID_UUID))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("GET 존재하지 않는 ID → 404")
    void getResultNotFoundReturns404() throws Exception {
        when(analysisService.getAnalysis(eq(VALID_UUID))).thenReturn(null);

        mockMvc.perform(get(BASE_URL + "/" + VALID_UUID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("GET 잘못된 UUID 형식 → 400")
    void getResultInvalidUuidReturns400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/invalid-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("잘못된 분석 ID 형식입니다."));
    }

    @Test
    @DisplayName("GET 로그 인젝션 시도 → 400")
    void getResultLogInjectionReturns400() throws Exception {
        mockMvc.perform(get(BASE_URL + "/abc\ndef"))
                .andExpect(status().isBadRequest());
    }
}
