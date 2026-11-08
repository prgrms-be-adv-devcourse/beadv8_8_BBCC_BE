package com.bbcc.kidly.boundedcontext.review.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.Deletion;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.Deletion.Outcome;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.ListUnavailableException;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredObject;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredPage;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 지우기(DeleteObject) · 목록(ListObjectsV2)을 로컬 가짜 HTTP 서버(JDK HttpServer)에 붙여 본다: 204 · 404는 지움,
 * 403 · 5xx · 시간 초과 · 연결 실패는 실패(종류 · 상태 코드만), 목록은 접두어 · 쪽 크기 · 이어 읽기 표지를 보내고 받은 key · 시각 · 다음 표지를
 * 그대로 돌려준다. 로그 · 실패 종류에 key를 남기지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class S3ReviewImageObjectStoreTest {

    private static final String BUCKET = "team04-kidly-s3";
    private static final String KEY = "dev/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.jpg";
    private static final String KEY2 = "dev/reviews/14/1b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.png";

    private HttpServer server;
    private final List<String> received = new ArrayList<>();
    private volatile int status = 204;
    private volatile Duration delay = Duration.ZERO;
    private volatile String body;
    private S3Client client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            synchronized (received) {
                received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath()
                    + (query == null ? "" : "?" + URLDecoder.decode(query, StandardCharsets.UTF_8)));
            }
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                if (body == null) {
                    exchange.sendResponseHeaders(status, -1);
                } else {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/xml");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                }
            } catch (IOException e) {
                // 클라이언트가 시간 초과로 먼저 끊은 경우
            }
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        client = client(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    @Test
    @DisplayName("버킷 · key로 DELETE를 보내고 204면 지움")
    void deletes() {
        assertThat(store().delete(KEY)).isEqualTo(Deletion.deleted());
        assertThat(received).containsExactly("DELETE /" + BUCKET + "/" + KEY);
    }

    @Test
    @DisplayName("404도 지운 것으로 본다(이미 없음, 다시 해도 안전)")
    void notFoundIsDeleted() {
        status = 404;

        assertThat(store().delete(KEY).outcome()).isEqualTo(Outcome.DELETED);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {403, 500, 503})
    @DisplayName("403 · 5xx는 실패이고 실패 종류는 예외 종류와 상태 코드뿐이다")
    void failsOnStatus(int code) {
        status = code;

        Deletion deletion = store().delete(KEY);

        assertThat(deletion.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(deletion.failureType()).isEqualTo("S3Exception:" + code);
    }

    @Test
    @DisplayName("시간 제한을 넘기거나 연결이 안 되면 실패(예외 종류만)")
    void failsOnTimeoutAndRefused() {
        delay = Duration.ofSeconds(2);
        Deletion timeout = store().delete(KEY);
        assertThat(timeout.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(timeout.failureType()).doesNotContain(KEY).doesNotContain("dev/");

        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.stop(0);
        try (S3Client refused = client(endpoint)) {
            Deletion deletion = new S3ReviewImageObjectStore(refused, BUCKET).delete(KEY);
            assertThat(deletion.outcome()).isEqualTo(Outcome.FAILED);
            assertThat(deletion.failureType()).isNotBlank().doesNotContain(KEY);
        }
    }

    @Test
    @DisplayName("목록은 접두어 · 쪽 크기 · 이어 읽기 표지를 보내고 key · 올린 시각 · 다음 표지를 돌려준다")
    void listsPage() {
        status = 200;
        body = listBody(true, "next-token-2",
            contents(KEY, "2026-11-01T10:00:00.000Z") + contents(KEY2, "2026-11-02T11:30:00.000Z"));

        StoredPage page = store().list("dev/reviews/", "token-1", 2);

        assertThat(page.objects()).containsExactly(
            new StoredObject(KEY, Instant.parse("2026-11-01T10:00:00Z")),
            new StoredObject(KEY2, Instant.parse("2026-11-02T11:30:00Z")));
        assertThat(page.nextContinuationToken()).isEqualTo("next-token-2");
        assertThat(received).singleElement().asString()
            .startsWith("GET /" + BUCKET)
            .contains("list-type=2").contains("prefix=dev/reviews/").contains("max-keys=2")
            .contains("continuation-token=token-1");
    }

    @Test
    @DisplayName("처음 쪽은 표지를 보내지 않고, 마지막 쪽이면 다음 표지가 null이다")
    void lastPage() {
        status = 200;
        body = listBody(false, null, contents(KEY, "2026-11-01T10:00:00.000Z"));

        StoredPage page = store().list("dev/reviews/", null, 500);

        assertThat(page.nextContinuationToken()).isNull();
        assertThat(page.objects()).hasSize(1);
        assertThat(received).singleElement().asString().doesNotContain("continuation-token");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {403, 500})
    @DisplayName("목록을 읽지 못하면(403 · 5xx) ListUnavailableException이고 메시지에 접두어 · key가 없다")
    void listFails(int code) {
        status = code;

        assertThatThrownBy(() -> store().list("dev/reviews/", null, 500))
            .isInstanceOf(ListUnavailableException.class)
            .hasMessageContaining(String.valueOf(code))
            .hasMessageNotContaining("dev/reviews");
    }

    @Test
    @DisplayName("실패 로그에 key · 접두어가 없다(상태 코드 · 예외 종류만)")
    void neverLogsKey(CapturedOutput output) {
        status = 403;
        store().delete(KEY);
        assertThatThrownBy(() -> store().list("dev/reviews/", null, 500));
        delay = Duration.ofSeconds(2);
        store().delete(KEY);

        assertThat(output.getAll()).doesNotContain(KEY).doesNotContain("0b6f3c1e").doesNotContain("dev/reviews");
        assertThat(store().name()).isEqualTo("s3");
        assertThat(store().available()).isTrue();
    }

    private S3ReviewImageObjectStore store() {
        return new S3ReviewImageObjectStore(client, BUCKET);
    }

    private static String contents(String key, String lastModified) {
        return "<Contents><Key>" + key + "</Key><LastModified>" + lastModified + "</LastModified>"
            + "<ETag>\"e\"</ETag><Size>10</Size><StorageClass>STANDARD</StorageClass></Contents>";
    }

    private static String listBody(boolean truncated, String nextToken, String contents) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
            + "<Name>" + BUCKET + "</Name><Prefix>dev/reviews/</Prefix><MaxKeys>2</MaxKeys>"
            + "<IsTruncated>" + truncated + "</IsTruncated>" + contents
            + (nextToken == null ? "" : "<NextContinuationToken>" + nextToken + "</NextContinuationToken>")
            + "</ListBucketResult>";
    }

    private static S3Client client(URI endpoint) {
        return S3Client.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
            .endpointOverride(endpoint)
            .forcePathStyle(true)
            .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofMillis(700)))
            .build();
    }
}
