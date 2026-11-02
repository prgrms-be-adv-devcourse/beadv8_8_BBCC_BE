# Gateway (#608)

nginx 뒤에서 모든 API 요청을 먼저 받는 실행 모듈(Spring Cloud Gateway, WebFlux). 업무 로직 · DB가 없다.

## 하는 일
요청이 거치는 순서다. 자세한 동작과 이유는 각 클래스 javadoc에 있다.
1. 보안 응답 헤더(`web/WebFilterConfig`): 모든 응답(400 포함)에 Spring Security 기본 보안 헤더를 붙인다
2. 경로 방화벽(`web/RequestFirewallFilter`): 정규화되지 않거나 위험한 경로 · 메서드를 400으로 막는다
3. 토큰 검사(`auth/AccessTokenFilter`): 밖에서 온 `X-Member-*`를 지우고, Bearer 토큰을 검증 · 무효화 확인해 `X-Member-*`를 붙인다. 실패하면 401
4. 역할 검사(`auth/RoleFilter`): `/api/v1/admin/**`는 ADMIN, `/api/v1/seller/**`는 SELLER. 없으면 401 · 403
5. CORS(`application.yml`의 `globalcors`) · 라우팅. 서비스는 CORS를 하지 않는다
- 1 · 2는 WebFilter(`web/WebFilterConfig`에 순서대로 등록), 3 · 4는 GlobalFilter(`RoleFilter` 순서 = `AccessTokenFilter.ORDER + 1`)
- 400 · 401 · 403 · 404 · 500 · 503 · 504 본문은 `web/ProblemResponse`가 쓴다(#668)
  - 404(`/internal/**`): 이름 있는 필터 `web/NotFoundGatewayFilterFactory`(`application.yml`에서 `NotFound`)
  - 503 · 504 · 500: `web/GatewayErrorWebExceptionHandler`
- 라우트: `application.yml`로만. `/internal/**`은 404, `GET /api/v1/products`(상품 목록, 경로가 정확히 같을 때만)는 `kidly.gateway.search-uri`(#526), 나머지는 kidly-app
- nginx가 넣은 `Host` · `X-Forwarded-*`는 덧붙이지 않고 그대로 넘긴다(`PreserveHostHeader`, `trusted-proxies`, `x-forwarded.*-enabled: false`). Gateway 자신은 `server.forward-headers-strategy: native`로 https · 원래 호스트로 본다(같은 출처 판단)

## 무효화 목록
- `revocation/AccessTokenRevocations`: 회원 ID → 무효화 시각, 메모리. 그 시각까지(초 단위, 같은 초 포함) 발급된 토큰은 401
- `kidly.member.events`의 `member.tokens-revoked`를 받아 채운다. 구독 그룹 `kidly.gateway`, 다른 종류는 건너뛴다
- 메모리라 다시 뜨면 빈다. 그래서 파티션을 받을 때마다 31분 전(Access Token 유효 기간 30분 + 시계 오차 60초)으로 되감아 다시 읽는다. 구독 그룹의 책갈피로 시작 위치를 정하지 않는다(31분 안에 메시지가 없는 파티션만 책갈피 위치에서 읽는다. 거기 있는 메시지는 보존 기간이 지나 결과가 같다)
- 읽기 실패는 재시도 없이 DLT `kidly.member.events.gateway.dlt` + ERROR 로그(본문 없이 `eventType` · `eventId` · 키)
- 31분 안에서 읽지 못해 DLT로 간 메시지는 Gateway가 다시 뜰 때마다 다시 읽혀 DLT로 다시 간다(ERROR 로그도 다시)
- `jwt.access-token-validity`는 kidly-app `auth.yml`과 같아야 한다
- 한계: 기동 직후 되감아 다 읽기 전에는 무효화된 토큰도 통과한다. 서비스는 토큰을 보지 않으므로 dev · prod 모두 이 사이는 실제로 뚫린다(prod 전환 전 readiness로 줄일 일)

## 지켜야 할 것
- CORS 값(허용 origin · 메서드 · 노출 헤더)은 Gateway(`application.yml`)에서만 바꾼다
- 회원 헤더 이름 · 역할 목록(쉼표) · 닉네임 인코딩(UTF-8 퍼센트 인코딩, 공백 `%20`) · 회원 ID 해석은 `MemberHeaders`, 역할은 `Role`, 토큰 클레임 이름은 `AccessTokenClaims`에 있다(모두 `shared`의 `com.bbcc.kidly.shared.auth`). Gateway · `common` · 회원이 같은 코드를 쓰므로 한 곳만 고친다
- 400 · 401 · 403 · 404 · 500 본문은 서비스와 같아야 한다(문구 `common` `CommonErrorCode`의 `INVALID_INPUT` · `UNAUTHORIZED` · `FORBIDDEN` · `NOT_FOUND` · `INTERNAL_ERROR`, 5필드 `ProblemDetails`). 모듈이 달라 테스트로 묶지 못하므로 한쪽을 바꾸면 `web/ProblemResponse`도 바꾼다
- 역할이 필요한 경로를 더하면 `auth/RoleFilter`의 `REQUIRED_ROLES`에 더한다. 서비스는 역할 경로를 검사하지 않는다
- 역할 검사(`auth/RoleFilter`)는 원래 요청 경로로 한다. 경로를 바꾸는 라우트 필터(`RewritePath` · `StripPrefix` 등)로 `/api/v1/admin` · `/api/v1/seller`에 닿게 하는 라우트를 만들지 않는다(바뀐 경로는 역할 검사를 받지 않는다)
- 라우트 추가: `application.yml`의 `routes`에 더한다. 검색처럼 kidly-app이 아닌 서비스의 경로는 `kidly-app` 라우트(`/**`) 위에 둔다(위에서부터 맞춘다)

## 서버 배포 (`deploy/`)
- dev · prod(#638). compose 서비스 `gateway`(profile `gateway`), 서버 `127.0.0.1:8083`(dev) · `127.0.0.1:8084`(prod)(`GATEWAY_PORT`), compose 네트워크로 `http://app:8080`에 넘긴다
- 상품 목록은 `KIDLY_SEARCH_URI`로 넘긴다. dev는 `.env.dev`에 `http://search-service:8080`(profile `search`도 켜져 있어야 한다), 값이 없는 prod는 기본값 `http://app:8080`이라 앱이 그대로 받는다
- dev · prod 모두 서비스(kidly-app · search-service)는 JWT를 보지 않고 Gateway가 붙인 `X-Member-*`로 로그인 회원을 만든다. 서비스는 Gateway 뒤에서만 열려야 한다
- nginx dev · 운영 블록의 `location /`만 Gateway로 간다. `/actuator/health`는 앱(dev 8081 · prod 8080)을 그대로 본다. nginx 설정은 배포로 바뀌지 않는다(`deploy/nginx/README.md`)

## 로컬 실행
- `docker compose up -d` 뒤 앱(`:apps:kidly-app:bootRun`)과 Gateway(`:apps:gateway:bootRun`, 8083)를 루트에서 띄운다. IDE는 `Kidly 앱 + Gateway` 실행 구성. 프론트 · Swagger는 8083으로 붙는다
