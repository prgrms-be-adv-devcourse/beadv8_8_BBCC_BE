package com.bbcc.kidly.boundedcontext.review.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.review.out.storage.ReviewImageUploadChecker.Presence;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.http.SdkHttpService;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3 업로드 확인(HeadObject)을 로컬 가짜 HTTP 서버(JDK HttpServer)에 붙여 본다: 200 있음, 404 없음, 403 · 5xx · 시간 초과 ·
 * 연결 실패 확인 불가. 로그에 주소 · key를 남기지 않는다.
 */
@ExtendWith(OutputCaptureExtension.class)
class S3ReviewImageUploadCheckerTest {

    private static final String BUCKET = "team04-kidly-s3";
    private static final String KEY = "test/reviews/13/0b6f3c1e-8a2d-4c5b-9e7f-1a2b3c4d5e6f.jpg";

    private HttpServer server;
    private final List<String> received = new ArrayList<>();
    private volatile int status = 200;
    private volatile Duration delay = Duration.ZERO;
    private S3Client client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            synchronized (received) {
                received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            }
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(status, -1);
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
    @DisplayName("동기 HTTP 구현은 모듈의 S3 SDK가 가져온 Apache 5 클라이언트 하나다(새 의존 없음, 자동 선택)")
    void syncHttpClientIsApache5() {
        List<String> services = new ArrayList<>();
        java.util.ServiceLoader.load(SdkHttpService.class)
            .forEach(service -> services.add(service.getClass().getName()));

        assertThat(services).singleElement().asString().contains("apache5");
    }

    @Test
    @DisplayName("버킷 · key로 HEAD를 보내고 200이면 있음")
    void presentOn200() {
        assertThat(checker().check("ignored-url", KEY)).isEqualTo(Presence.PRESENT);
        assertThat(received).containsExactly("HEAD /" + BUCKET + "/" + KEY);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"404, ABSENT", "403, UNKNOWN", "500, UNKNOWN", "503, UNKNOWN", "400, UNKNOWN"})
    @DisplayName("404는 없음, 그 밖(403 · 5xx · 그 외)은 확인 불가")
    void statusMapping(int code, Presence expected) {
        status = code;

        assertThat(checker().check("ignored-url", KEY)).isEqualTo(expected);
    }

    @Test
    @DisplayName("시간 제한을 넘기면 확인 불가")
    void timeout() {
        delay = Duration.ofSeconds(2);

        assertThat(checker().check("ignored-url", KEY)).isEqualTo(Presence.UNKNOWN);
    }

    @Test
    @DisplayName("연결이 안 되면 확인 불가")
    void connectionRefused() {
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        server.stop(0);
        try (S3Client refused = client(endpoint)) {
            assertThat(new S3ReviewImageUploadChecker(refused, BUCKET).check("ignored-url", KEY))
                .isEqualTo(Presence.UNKNOWN);
        }
    }

    @Test
    @DisplayName("확인 실패 로그에 주소 · key가 없다(상태 코드 · 예외 종류만)")
    void neverLogsKey(CapturedOutput output) {
        status = 403;
        checker().check("https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/" + KEY, KEY);
        delay = Duration.ofSeconds(2);
        checker().check("https://team04-kidly-s3.s3.ap-northeast-2.amazonaws.com/" + KEY, KEY);

        assertThat(output.getAll()).doesNotContain(KEY).doesNotContain("0b6f3c1e");
        assertThat(checker().name()).isEqualTo("s3");
    }

    private S3ReviewImageUploadChecker checker() {
        return new S3ReviewImageUploadChecker(client, BUCKET);
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
