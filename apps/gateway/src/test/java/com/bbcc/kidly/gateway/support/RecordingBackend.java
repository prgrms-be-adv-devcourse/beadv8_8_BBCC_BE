package com.bbcc.kidly.gateway.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;

/**
 * Gateway 뒤의 서비스(kidly-app · search-service) 대신 받는 가짜 백엔드. 마지막으로 받은 요청만 기억하고 200 ok로 답한다.
 * 응답 헤더를 더 붙이려면 addResponseHeader를, 응답을 늦추려면 delayResponse를 쓴다. reset()이 둘 다 지운다.
 */
public class RecordingBackend implements AutoCloseable {

    // Gateway가 허용하는 프론트 origin(application-test.yml cors.allowed-origins)
    public static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final byte[] OK = "ok".getBytes(UTF_8);

    private final HttpServer server;
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();
    private final Map<String, String> extraResponseHeaders = new ConcurrentHashMap<>();
    private volatile Duration responseDelay = Duration.ZERO;

    private RecordingBackend(HttpServer server) {
        this.server = server;
    }

    public static RecordingBackend start() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var backend = new RecordingBackend(server);
            server.createContext("/", backend::handle);
            // 가상 스레드마다 처리해, 지연 응답(delayResponse)을 기다리는 요청이 공유 컨텍스트의 다음 테스트 요청을 막지 않게 한다
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return backend;
        } catch (IOException e) {
            throw new UncheckedIOException("가짜 백엔드를 띄우지 못했습니다", e);
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void reset() {
        lastRequest.set(null);
        extraResponseHeaders.clear();
        responseDelay = Duration.ZERO;
    }

    public void addResponseHeader(String name, String value) {
        extraResponseHeaders.put(name, value);
    }

    public void delayResponse(Duration delay) {
        responseDelay = delay;
    }

    public Optional<RecordedRequest> lastRequest() {
        return Optional.ofNullable(lastRequest.get());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            lastRequest.set(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI(),
                Map.copyOf(exchange.getRequestHeaders())));
            sleep(responseDelay);
            var headers = exchange.getResponseHeaders();
            extraResponseHeaders.forEach(headers::add);
            exchange.sendResponseHeaders(HttpStatus.OK.value(), OK.length);
            exchange.getResponseBody().write(OK);
        }
    }

    private static void sleep(Duration delay) {
        if (delay.isZero()) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("응답 지연 중 끊겼습니다", e);
        }
    }

    /**
     * 백엔드가 받은 요청. uri는 경로와 쿼리다. 헤더 이름은 대소문자를 가리지 않는다.
     */
    public record RecordedRequest(String method, URI uri, Map<String, List<String>> headers) {

        public List<String> header(String name) {
            return headers.entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(name))
                .flatMap(header -> header.getValue().stream())
                .toList();
        }
    }
}
