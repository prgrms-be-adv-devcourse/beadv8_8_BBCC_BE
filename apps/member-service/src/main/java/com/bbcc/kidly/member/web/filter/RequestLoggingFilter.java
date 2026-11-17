package com.bbcc.kidly.member.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/** 요청 본문을 버퍼링하지 않고 정규화 경로·상태·소요시간·traceId만 기록한다. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Request-Id";
    private static final Pattern TRACE_ID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final PathPattern ACTUATOR_PATH = PathPatternParser.defaultInstance.parse("/actuator/**");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String supplied = request.getHeader(TRACE_ID_HEADER);
        String traceId = supplied != null && TRACE_ID_PATTERN.matcher(supplied).matches()
            ? supplied : UUID.randomUUID().toString();
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        boolean actuator = ACTUATOR_PATH.matches(PathContainer.parsePath(request.getRequestURI()));
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                logRequest(request, response.getStatus(), actuator, start);
            } finally {
                MDC.remove(TRACE_ID_MDC_KEY);
            }
        }
    }

    private static void logRequest(HttpServletRequest request, int status, boolean actuator, long start) {
        if (actuator && HttpStatusCode.valueOf(status).is2xxSuccessful()) {
            return;
        }
        String route = Optional.ofNullable(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
            .map(Object::toString)
            .orElse(actuator ? "/actuator" : "UNKNOWN");
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        log.atInfo()
            .addKeyValue("http.request.method", request.getMethod())
            // Keep the existing field for dashboards, but never put the raw URL in it.
            .addKeyValue("url.path", route)
            .addKeyValue("http.route", route)
            .addKeyValue("http.response.status_code", status)
            .addKeyValue("duration_ms", duration)
            .log("{} {} - {} ({}ms)", request.getMethod(), route, status, duration);
    }
}
