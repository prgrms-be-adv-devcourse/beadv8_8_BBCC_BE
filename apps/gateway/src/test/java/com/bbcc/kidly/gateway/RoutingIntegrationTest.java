package com.bbcc.kidly.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.gateway.support.GatewayTestSupport;
import com.bbcc.kidly.gateway.support.IntegrationTest;
import com.bbcc.kidly.gateway.support.RecordingBackend;
import com.bbcc.kidly.gateway.support.RecordingBackendConfiguration;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

/**
 * 내부 API를 막고, 상품 목록은 검색 서비스로(#526), 나머지는 kidly-app으로 보내는지, CORS를 Gateway가 처리하는지 본다(#608).
 */
@IntegrationTest
class RoutingIntegrationTest extends GatewayTestSupport {

    private static final String ORIGIN = RecordingBackend.ALLOWED_ORIGIN;
    // nginx dev 블록이 받는 이름. 허용 origin(프론트)에는 없다
    private static final String NGINX_HOST = "dev-api.example";
    private static final String CLIENT_IP = "1.2.3.4";

    @Autowired
    @Qualifier(RecordingBackendConfiguration.SEARCH)
    private RecordingBackend searchBackend;

    @BeforeEach
    void resetSearchBackend() {
        searchBackend.reset();
    }

    @Test
    @DisplayName("서비스끼리만 부르는 내부 API는 404이고 넘기지 않는다")
    void blocksInternalApi() {
        client.get().uri("/internal/members/1").exchange()
            .expectStatus().isNotFound();

        assertThat(backend.lastRequest()).isEmpty();
        assertThat(searchBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("상품 목록 조회는 같은 경로 · 쿼리로 검색 서비스에 넘긴다")
    void routesProductListToSearchService() {
        client.get().uri("/api/v1/products?keyword=원피스&sort=NEWEST&page=0").exchange()
            .expectStatus().isOk();

        assertThat(searchBackend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.uri().getPath()).isEqualTo("/api/v1/products");
            assertThat(request.uri().getQuery()).isEqualTo("keyword=원피스&sort=NEWEST&page=0");
        });
        assertThat(backend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("상품 상세는 상품(kidly-app)에 넘긴다")
    void routesProductDetailToKidlyApp() {
        client.get().uri("/api/v1/products/1").exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/products/1")));
        assertThat(searchBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("상품 목록 경로라도 GET이 아니면 kidly-app에 넘긴다")
    void routesNonGetProductsToKidlyApp() {
        client.post().uri("/api/v1/products").exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(request ->
            assertThat(request.method()).isEqualTo("POST"));
        assertThat(searchBackend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("그 밖의 요청은 같은 경로 · 쿼리로 kidly-app에 넘긴다")
    void routesToKidlyApp() {
        client.get().uri("/api/v1/members/me?x=1").exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.uri()).isEqualTo(URI.create("/api/v1/members/me?x=1"));
        });
    }

    @Test
    @DisplayName("프리플라이트는 Gateway가 답하고 넘기지 않는다")
    void answersPreflight() {
        preflight(ORIGIN)
            .expectStatus().isOk()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");

        assertThat(backend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("허용하지 않은 origin의 프리플라이트는 403이다")
    void rejectsPreflightFromUnknownOrigin() {
        preflight("https://evil.example")
            .expectStatus().isForbidden();

        assertThat(backend.lastRequest()).isEmpty();
    }

    @Test
    @DisplayName("Gateway가 만든 401에도 CORS 헤더가 붙는다")
    void addsCorsHeadersToUnauthorized() {
        client.get().uri("/api/v1/products")
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer abc")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN)
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }

    @Test
    @DisplayName("서비스 응답에 Gateway가 CORS 헤더를 붙인다(서비스는 CORS를 하지 않는다)")
    void addsCorsHeadersToBackendResponse() {
        corsGet()
            .expectStatus().isOk()
            .expectHeader().values(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                values -> assertThat(values).containsExactly(ORIGIN))
            .expectHeader().values(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                values -> assertThat(values).containsExactly("true"))
            .expectHeader().values(HttpHeaders.VARY, values -> assertThat(values).contains(HttpHeaders.ORIGIN));
    }

    @Test
    @DisplayName("프론트가 traceId를 읽을 수 있게 X-Request-Id를 노출한다")
    void exposesRequestId() {
        corsGet()
            .expectStatus().isOk()
            .expectHeader().values(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                values -> assertThat(values).containsExactly("X-Request-Id"));
    }

    @Test
    @DisplayName("nginx가 넣은 Host · X-Forwarded-*를 앱이 nginx 직결 때와 같은 값으로 받는다")
    void forwardsNginxHeaders() {
        client.get().uri("/api/v1/products/1")
            .headers(this::nginxHeaders)
            .exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).hasValueSatisfying(request -> {
            assertThat(request.header(HttpHeaders.HOST)).containsExactly(NGINX_HOST);
            // Tomcat은 오른쪽부터 내부 프록시(사설 · 루프백)를 건너뛴 첫 값을 클라이언트로 본다
            var forwardedFor = request.header("X-Forwarded-For").stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .toList();
            assertThat(forwardedFor).first().isEqualTo(CLIENT_IP);
            assertThat(forwardedFor.subList(1, forwardedFor.size())).allMatch(RoutingIntegrationTest::isInternal);
            // 값이 여럿이면(https,http) Tomcat이 https로 보지 않는다
            assertThat(request.header("X-Forwarded-Proto")).containsExactly("https");
            assertThat(request.header("X-Forwarded-Host")).containsExactly(NGINX_HOST);
            assertThat(request.header("X-Forwarded-Port")).containsExactly("443");
            // nginx는 Forwarded를 넣지 않는다. Gateway도 만들지 않는다
            assertThat(request.header("Forwarded")).isEmpty();
        });
    }

    @Test
    @DisplayName("nginx 뒤의 같은 출처 요청(dev Swagger)은 CORS로 막지 않는다")
    void treatsSameOriginBehindNginxAsSameOrigin() {
        client.post().uri("/api/v1/orders")
            .headers(this::nginxHeaders)
            .header(HttpHeaders.ORIGIN, "https://" + NGINX_HOST)
            .exchange()
            .expectStatus().isOk();

        assertThat(backend.lastRequest()).isPresent();
    }

    private ResponseSpec preflight(String origin) {
        return client.options().uri("/api/v1/orders")
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
            .exchange();
    }

    private ResponseSpec corsGet() {
        return client.get().uri("/api/v1/products")
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .exchange();
    }

    // deploy/nginx/kidly.conf.template 공통 설정이 넣는 헤더
    private void nginxHeaders(HttpHeaders headers) {
        headers.set(HttpHeaders.HOST, NGINX_HOST);
        headers.set("X-Real-IP", CLIENT_IP);
        headers.set("X-Forwarded-For", CLIENT_IP);
        headers.set("X-Forwarded-Proto", "https");
        headers.set("X-Forwarded-Host", NGINX_HOST);
        headers.set("X-Forwarded-Port", "443");
    }

    private static boolean isInternal(String ip) {
        try {
            var address = InetAddress.getByName(ip);
            return address.isLoopbackAddress() || address.isSiteLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
