package com.example.wemadeassignment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("접속 로그 분석 API")
                        .description("CSV 접속 로그 파일을 업로드하면 파싱/집계 후 IP 정보를 조회하여 통계 리포트를 제공합니다.")
                        .version("1.0.0"));
    }
}
