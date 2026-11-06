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
    private volatile String body = verdict("safe");
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

    static String verdict(String content) {
        return "{\"id\":\"x\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
            + content + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"total_tokens\":10}}";
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
