package com.bbcc.kidly.global.filter;

import com.bbcc.kidly.shared.auth.MemberHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.core.JacksonException;

/**
 * 요청마다 traceId를 MDC에 넣고 요청이 끝나면 로그 한 줄을 남긴다.
 * 클라이언트가 UUID 형식의 X-Request-Id를 보내면 그 값을 이어서 쓴다.
 * actuator 요청(헬스체크 · 메트릭 수집)은 2xx가 아닐 때만 로그를 남긴다.
 * 로그에는 IP · 경로 패턴 · 쿼리 · User-Agent · 헤더 · JSON 본문(각 UTF-8 3,000바이트)을 비밀값을 가려 붙인다({@link LogMasker}).
 * 회원 ID는 Gateway가 붙인 X-Member-Id 헤더로 MDC에 넣고(Long 범위의 숫자가 아니면 넣지 않는다) 요청이 끝나면 지운다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String MEMBER_ID_MDC_KEY = "memberId";
    public static final String TRACE_ID_HEADER = "X-Request-Id";
    private static final Pattern TRACE_ID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final PathPattern ACTUATOR_PATH = PathPatternParser.defaultInstance.parse("/actuator/**");
    // 토큰 · 인가 코드(인증, 개발용 토큰)와 결제 데이터(토스 웹훅)가 오가는 경로는 본문을 남기지 않는다
    private static final List<PathPattern> BODY_HIDDEN_PATHS =
        Stream.of("/api/v1/auth/**", "/api/v1/dev/tokens", "/api/v1/webhooks/toss")
            .map(PathPatternParser.defaultInstance::parse)
            .toList();
    // 로그 한 줄이 Docker 줄 한도(16KB)를 넘지 않게 본문은 각각 이만큼만 남긴다
    private static final int BODY_MAX_BYTES = 3_000;
    // 본문은 이만큼까지만 읽는다. 요청은 이만큼만 담아 두고(더 크면 JSON이 끊겨 가릴 수 없어 표시만), 응답은 더 크면 크기만 남긴다
    private static final int BODY_READ_LIMIT = 64 * 1024;
    private static final String UNREADABLE_JSON = "(JSON으로 읽지 못해 생략)";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        memberId(request).ifPresent(memberId -> MDC.put(MEMBER_ID_MDC_KEY, String.valueOf(memberId)));
        response.setHeader(TRACE_ID_HEADER, traceId);

        var path = PathContainer.parsePath(request.getRequestURI());
        boolean actuator = ACTUATOR_PATH.matches(path);
        // actuator 는 본문을 남기지 않으므로 감싸지 않는다(Prometheus 수집 응답을 버퍼에 담지 않는다)
        var loggedRequest = actuator ? request : new ContentCachingRequestWrapper(request, BODY_READ_LIMIT);
        var loggedResponse = actuator ? response : new ContentCachingResponseWrapper(response);
        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(loggedRequest, loggedResponse);
        } finally {
            // 로그를 남기다 실패해도 MDC를 비우고 응답 본문을 보낸다
            try {
                long duration = System.currentTimeMillis() - startTime;
                int status = loggedResponse.getStatus();
                if (!actuator || !HttpStatusCode.valueOf(status).is2xxSuccessful()) {
                    // 비동기 응답은 본문이 아직 다 쓰이지 않았다
                    boolean withBody = !request.isAsyncStarted()
                        && BODY_HIDDEN_PATHS.stream().noneMatch(pattern -> pattern.matches(path));
                    logRequest(loggedRequest, loggedResponse, duration, withBody);
                }
            } finally {
                MDC.remove(TRACE_ID_MDC_KEY);
                MDC.remove(MEMBER_ID_MDC_KEY);
                if (loggedResponse instanceof ContentCachingResponseWrapper cachedResponse) {
                    cachedResponse.copyBodyToResponse();
                }
            }
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response,
        long duration, boolean withBody) {
        int status = response.getStatus();
        var event = log.atInfo()
            .addKeyValue("http.request.method", request.getMethod())
            .addKeyValue("url.path", request.getRequestURI())
            .addKeyValue("http.response.status_code", status)
            .addKeyValue("duration_ms", duration)
            .addKeyValue("client.ip", request.getRemoteAddr())
            .addKeyValue("http.request.headers", LogMasker.headers(requestHeaderNames(request),
                name -> Collections.list(request.getHeaders(name))))
            .addKeyValue("http.response.headers", LogMasker.headers(response.getHeaderNames(), response::getHeaders));
        addIfPresent(event, "http.route", request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        Optional.ofNullable(request.getQueryString())
            .map(LogMasker::query)
            .ifPresent(query -> event.addKeyValue("url.query", query));
        addIfPresent(event, "user_agent.original", request.getHeader(HttpHeaders.USER_AGENT));
        if (withBody && request instanceof ContentCachingRequestWrapper cachedRequest
            && response instanceof ContentCachingResponseWrapper cachedResponse) {
            addIfPresent(event, "http.request.body.content",
                bodyContent(cachedRequest.getContentAsByteArray(), cachedRequest.getContentType()));
            addIfPresent(event, "http.response.body.content",
                bodyContent(cachedResponse.getContentAsByteArray(), cachedResponse.getContentType()));
        }
        event.log("{} {} - {} ({}ms)", request.getMethod(), request.getRequestURI(), status, duration);
    }

    // User-Agent는 user_agent.original로 따로 남기므로 헤더에서 뺀다
    private static List<String> requestHeaderNames(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames()).stream()
            .filter(name -> !HttpHeaders.USER_AGENT.equalsIgnoreCase(name))
            .toList();
    }

    private static void addIfPresent(LoggingEventBuilder event, String key, Object value) {
        if (value != null) {
            event.addKeyValue(key, value);
        }
    }

    /**
     * JSON 본문만 비밀값을 가려 UTF-8 3,000바이트까지 돌려준다.
     * 그 밖의 형식(텍스트 · multipart · 바이너리)은 가릴 방법이 없거나 읽을 수 없어 null.
     */
    private static String bodyContent(byte[] content, String contentType) {
        if (content.length == 0 || contentType == null) {
            return null;
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return null;
        }
        if (!"json".equals(mediaType.getSubtype()) && !"json".equals(mediaType.getSubtypeSuffix())) {
            return null;
        }
        if (content.length > BODY_READ_LIMIT) {
            return "(" + content.length + "바이트, 생략)";
        }
        var json = new String(content, Optional.ofNullable(mediaType.getCharset()).orElse(StandardCharsets.UTF_8));
        try {
            return LogMasker.truncateUtf8(LogMasker.json(json), BODY_MAX_BYTES);
        } catch (JacksonException e) {
            return UNREADABLE_JSON;
        }
    }

    // Gateway가 밖에서 온 X-Member-* 헤더를 지우고 붙인 값이다. 로그인이 필요 없는 API의 로그에도 남는다
    private static Optional<Long> memberId(HttpServletRequest request) {
        return MemberHeaders.parseMemberId(request.getHeader(MemberHeaders.MEMBER_ID));
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        return (traceId != null && TRACE_ID_PATTERN.matcher(traceId).matches())
            ? traceId
            : UUID.randomUUID().toString();
    }
}
