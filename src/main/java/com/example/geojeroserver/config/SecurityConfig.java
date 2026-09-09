package com.example.geojeroserver.config;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.example.geojeroserver.exception.ErrorCode;
import com.example.geojeroserver.logging.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityConfig.CorsProperties.class)
public class SecurityConfig {

    @ConfigurationProperties(prefix = "cors")
    public record CorsProperties(List<String> allowedOrigins) {
    }

    private final CorsProperties corsProperties;

    public SecurityConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationEntryPoint authenticationEntryPoint,
                                           AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 필터 체인에서 터지는 인증/인가 예외는 DispatcherServlet에 도달하지 않아
                // @RestControllerAdvice가 잡지 못한다. 여기서 직접 ProblemDetail을 내려준다.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    /**
     * 인증 실패(401). JWT 필터를 붙인 뒤에는 만료/위조를 구분해
     * ErrorCode.TOKEN_EXPIRED / INVALID_TOKEN 중 하나를 골라 내려주면 된다.
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) ->
                writeProblemDetail(objectMapper, request, response, ErrorCode.INVALID_TOKEN);
    }

    /** 인가 실패(403). */
    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) ->
                writeProblemDetail(objectMapper, request, response, ErrorCode.ACCESS_DENIED);
    }

    private void writeProblemDetail(ObjectMapper objectMapper,
                                    HttpServletRequest request,
                                    HttpServletResponse response,
                                    ErrorCode errorCode) throws IOException {

        ProblemDetail problemDetail = errorCode.toProblemDetail();
        problemDetail.setInstance(URI.create(request.getRequestURI()));

        // TraceIdFilter가 Security 필터 체인보다 먼저 돌기 때문에 여기서도 MDC를 읽을 수 있다.
        String traceId = TraceIdFilter.currentTraceId();
        if (traceId != null) {
            problemDetail.setProperty(TraceIdFilter.TRACE_ID, traceId);
        }

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problemDetail);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // allowedHeaders와 별개로, 브라우저가 응답 헤더를 읽으려면 명시적으로 노출해야 한다.
        configuration.setExposedHeaders(List.of(TraceIdFilter.TRACE_ID_HEADER));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
