package com.example.geojeroserver.api;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "거제로 API", version = "0.5.0",
    description = "계약 v0 — 판정·시간표·출발·알림·POI·코스 실구현. "
        + "POI 상세의 TourAPI 연동과 카카오 인증·일정 저장은 후속 태스크(현재 폴백/목)."))
public class OpenApiConfig {}
