package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 호환 채팅 API를 흉내 내는 로컬 HTTP 서버(JDK HttpServer). 받은 요청(경로 · 헤더 · 본문)을 남긴다.
 */
final class FakeChatServer {

    record Received(String path, Map<String, List<String>> headers, String body) {
    }

    private final HttpServer server;
    private final List<Received> received = new ArrayList<>();
    private volatile int status = 200;
    private volatile String body = reply("{\"violation\":0,\"category\":null}");
    private volatile Duration delay = Duration.ZERO;

    private FakeChatServer(HttpServer server) {
        this.server = server;
    }

    static FakeChatServer start() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            FakeChatServer fake = new FakeChatServer(http);
            http.createContext("/", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                synchronized (fake.received) {
                    fake.received.add(new Received(exchange.getRequestURI().getPath(),
                        Map.copyOf(exchange.getRequestHeaders()), request));
                }
                try {
                    Thread.sleep(fake.delay.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                byte[] bytes = fake.body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(fake.status, bytes.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                } catch (IOException e) {
                    // 클라이언트가 시간 초과로 먼저 끊은 경우
                }
            });
            http.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            http.start();
            return fake;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 채팅 응답 하나(끝난 까닭 stop). content는 모델이 낸 글 그대로이고 JSON 문자열로 감싸 넣는다.
     */
    static String reply(String content) {
        return reply(content, "stop");
    }

    static String reply(String content, String finishReason) {
        return "{\"id\":\"x\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
            + quote(content) + "},\"finish_reason\":" + (finishReason == null ? "null" : quote(finishReason))
            + "}],\"usage\":{\"total_tokens\":10}}";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    void respond(int status, String body) {
        respondAfter(Duration.ZERO, status, body);
    }

    void respondAfter(Duration delay, int status, String body) {
        this.delay = delay;
        this.status = status;
        this.body = body;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/openai/v1";
    }

    List<Received> received() {
        synchronized (received) {
            return List.copyOf(received);
        }
    }

    void stop() {
        server.stop(0);
    }
}
