package com.example.geojeroserver.logging;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 traceId를 발급해 MDC / 응답 헤더 / 에러 응답 body에 함께 싣는다.
 * 유저가 보내온 에러 화면의 traceId 하나로 서버 로그를 바로 찾을 수 있게 하는 것이 목적이다.
 *
 * Security 필터 체인(기본 order -100)보다 먼저 돌아야
 * 인증 실패 응답에도 traceId가 붙으므로 최우선 순위로 등록한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 클라이언트가 보낸 traceId를 이어받되 형식이 맞을 때만 신뢰한다.
     * 외부 입력을 그대로 로그에 흘리면 개행 주입으로 로그를 위조할 수 있다.
     */
    private static final Pattern ALLOWED_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private static final int GENERATED_LENGTH = 16;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String traceId = resolveTraceId(request);

        request.setAttribute(TRACE_ID, traceId);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    /** 서블릿 ERROR 디스패치로 재진입할 때도 같은 traceId를 유지하기 위해 필터를 다시 태운다. */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    private String resolveTraceId(HttpServletRequest request) {
        // ERROR 디스패치 재진입이면 최초 요청에서 발급한 값을 그대로 쓴다.
        if (request.getAttribute(TRACE_ID) instanceof String reused) {
            return reused;
        }

        String inbound = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.hasText(inbound) && ALLOWED_TRACE_ID.matcher(inbound).matches()) {
            return inbound;
        }

        return UUID.randomUUID().toString().replace("-", "").substring(0, GENERATED_LENGTH);
    }

    /**
     * 현재 요청의 traceId. 필터를 타지 않은 스레드(스케줄러, @Async 등)에서는 null이다.
     * MDC는 스레드에 묶이므로 별도 스레드로 넘길 때는 직접 복사해야 한다.
     */
    @Nullable
    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID);
        return StringUtils.hasText(traceId) ? traceId : null;
    }
}
