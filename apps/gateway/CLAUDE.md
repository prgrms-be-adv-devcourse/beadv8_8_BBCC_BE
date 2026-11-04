# Gateway (#608)

nginx 뒤에서 모든 API 요청을 먼저 받는 실행 모듈(Spring Cloud Gateway, WebFlux). 업무 로직 · DB가 없다.

## 하는 일
요청이 거치는 순서다. 자세한 동작과 이유는 각 클래스 javadoc에 있다.
1. 보안 응답 헤더(`web/WebFilterConfig`): 모든 응답(400 포함)에 Spring Security 기본 보안 헤더를 붙인다
2. 요청 ID(`web/RequestIdFilter`): 요청마다 `X-Request-Id`를 새로 만들어 클라이언트가 보낸 값을 덮어쓰고 응답 헤더로도 돌려준다. Gateway 로그(`web/RequestFirewallFilter` · `web/GatewayErrorWebExceptionHandler`)에는 key-value `traceId`로 남긴다. 서비스(`RequestLoggingFilter`)는 같은 헤더 값을 MDC `traceId`로 써서, 값 하나로 Gateway 로그부터 서비스 로그까지 한 요청을 찾는다(#683)
3. 경로 방화벽(`web/RequestFirewallFilter`): 정규화되지 않거나 위험한 경로 · 메서드를 400으로 막는다
4. 토큰 검사(`auth/AccessTokenFilter`): 밖에서 온 `X-Member-*` · `X-Admin-*` · `X-Child-*`를 지우고, Bearer 토큰을 검증 · 무효화 확인해 주체별 헤더를 붙인다. 실패하면 401
5. 역할 검사(`auth/RoleFilter`): `/api/v1/admin/**`는 관리자 주체, `/api/v1/seller/**`는 SELLER 회원 주체, `/api/v1/child/**`는 자녀 주체. 없으면 401 · 403
6. CORS(`application.yml`의 `globalcors`) · 라우팅. 서비스는 CORS를 하지 않는다
- 1 · 2 · 3은 WebFilter(`web/WebFilterConfig`에 순서대로 등록), 4 · 5는 GlobalFilter(`RoleFilter` 순서 = `AccessTokenFilter.ORDER + 1`)
- 400 · 401 · 403 · 404 · 500 · 503 · 504 본문은 `web/ProblemResponse`가 쓴다(#668)
  - 404(`/internal/**`): 이름 있는 필터 `web/NotFoundGatewayFilterFactory`(`application.yml`에서 `NotFound`)
  - 503 · 504 · 500: `web/GatewayErrorWebExceptionHandler`
  - 백엔드 호출 timeout(`application.yml`의 `spring.cloud.gateway.server.webflux.httpclient`): 연결 3초, 응답 55초. 55초는 결제 승인(Toss 연결 3초 + 읽기 30초)보다 길고 nginx 기본 `proxy_read_timeout` 60초보다 짧다. 테스트는 `application-test.yml`에서 응답 1초로 줄인다
  - 추천 라우트만 응답 3초(라우트 `metadata.response-timeout`, #704). 추천이 느려도 화면이 추천 영역만 비우고 넘어가게 한다. 테스트는 전체 1초 설정과 상관없이 라우트 값을 쓴다(`BackendTimeoutIntegrationTest`)
- 라우트: `application.yml`로만. `/internal/**`은 404, `GET /api/v1/products`(상품 목록, 경로가 정확히 같을 때만)는 `kidly.gateway.search-uri`(#526), `/api/v1/children` · `/api/v1/children/**`(자녀 프로필)는 `kidly.gateway.childprofile-uri`(#682), `/v3/api-docs/childprofile`은 같은 서비스의 `/v3/api-docs`(kidly-app Swagger의 '자녀 프로필' 문서, `SetPath`), `/api/v1/recommendations/**`(추천)는 `kidly.gateway.recommend-uri`(#704), `/v3/api-docs/recommend`는 같은 서비스의 `/v3/api-docs`('추천' 문서), `/api/v1/seller/settlement-targets` · `/api/v1/seller/settlements[/**]` · `/api/v1/admin/settlements[/**]` · `/api/v1/admin/settlement-runs/**`(정산)는 `kidly.gateway.settlement-uri`(#729), 회원 · 인증 · 판매자(`/api/v1/auth/**` · `/api/v1/members/**` · `/api/v1/seller/me/**` · `/api/v1/admin/admins/**` · `/api/v1/admin/members/**` · `/api/v1/admin/sellers/**` · `/api/v1/admin/seller-applications/**` · `/api/v1/dev/**`)는 `kidly.gateway.member-uri`, `/v3/api-docs/member`는 같은 서비스의 `/v3/api-docs`, `/api/v1/payments/**` · `/api/v1/wallets/**` · `/api/v1/webhooks/toss` · `/api/v1/admin/payments/**` · `/api/v1/admin/wallets/**`(결제 · 지갑 · 충전 · 토스 웹훅 · 결제 관리자)는 `kidly.gateway.payment-uri`(#525, 응답 제한은 전체 55초), `/v3/api-docs/payment`는 같은 서비스의 `/v3/api-docs`('결제' 문서), `/v3/api-docs/settlement`는 같은 서비스의 `/v3/api-docs`('정산' 문서), `/api/v1/products/**` · `/api/v1/categories/**` · `/api/v1/seller/products/**` · `/api/v1/seller/product-images/**` · `/api/v1/seller/options/**` · `/api/v1/admin/products/**` · `/api/v1/admin/categories/**` · `/api/v1/admin/product-types/**` · `/api/v1/admin/attributes/**` · `/api/v1/admin/measurement-items/**`(상품 · 카테고리 · 판매자 · 관리자 상품 API)는 `kidly.gateway.product-uri`(#791, 상품 목록 `GET /api/v1/products`는 위의 검색 라우트가 먼저 잡는다), 나머지는 kidly-app
- nginx가 넣은 `Host` · `X-Forwarded-*`는 덧붙이지 않고 그대로 넘긴다(`PreserveHostHeader`, `trusted-proxies`, `x-forwarded.*-enabled: false`). Gateway 자신은 `server.forward-headers-strategy: native`로 https · 원래 호스트로 본다(같은 출처 판단)

## 무효화 목록
- `revocation/AccessTokenRevocations`: `PrincipalId`(주체 종류, 주체 ID) → 무효화 시각, 메모리. 그 시각까지(초 단위, 같은 초 포함) 발급된 토큰은 401
- `kidly.auth.events`의 `auth.tokens-revoked`를 받아 채운다(발행은 인증 도메인, `domains/auth`). 구독 그룹 `kidly.gateway`, 다른 종류는 건너뛴다
- 메모리라 다시 뜨면 빈다. 그래서 파티션을 받을 때마다 31분 전(Access Token 유효 기간 30분 + 시계 오차 60초)으로 되감아 다시 읽는다. 구독 그룹의 책갈피로 시작 위치를 정하지 않는다(31분 안에 메시지가 없는 파티션만 책갈피 위치에서 읽는다. 거기 있는 메시지는 보존 기간이 지나 결과가 같다)
- 읽기 실패는 재시도 없이 DLT `kidly.auth.events.gateway.dlt` + ERROR 로그(본문 없이 `eventType` · `eventId` · 키)
- 31분 안에서 읽지 못해 DLT로 간 메시지는 Gateway가 다시 뜰 때마다 다시 읽혀 DLT로 다시 간다(ERROR 로그도 다시)
- `jwt.access-token-validity`는 인증 도메인(`domains/auth`)의 `auth.yml`과 같아야 한다
- 한계: 기동 직후 되감아 다 읽기 전에는 무효화된 토큰도 통과한다. 서비스는 토큰을 보지 않으므로 dev · prod 모두 이 사이는 실제로 뚫린다(prod 전환 전 readiness로 줄일 일)

## 지켜야 할 것
- CORS 값(허용 origin · 메서드 · 노출 헤더)은 Gateway(`application.yml`)에서만 바꾼다
- 주체 헤더 이름 · 접두어는 `PrincipalHeaders`, 토큰 주체는 `PrincipalId`에 있다. 회원 헤더 이름 · 역할 목록(쉼표) · 닉네임 인코딩(UTF-8 퍼센트 인코딩, 공백 `%20`) · 회원 ID 해석은 `MemberHeaders`, 역할은 `Role`, 토큰 클레임 이름은 `AccessTokenClaims`에 있다(모두 `shared`의 `com.bbcc.kidly.shared.auth`). Gateway · `common` · 회원이 같은 코드를 쓰므로 한 곳만 고친다
- 400 · 401 · 403 · 404 · 500 본문은 서비스와 같아야 한다(문구 `common` `CommonErrorCode`의 `INVALID_INPUT` · `UNAUTHORIZED` · `FORBIDDEN` · `NOT_FOUND` · `INTERNAL_ERROR`, 5필드 `ProblemDetails`). 모듈이 달라 테스트로 묶지 못하므로 한쪽을 바꾸면 `web/ProblemResponse`도 바꾼다
- 역할이 필요한 경로를 더하면 `auth/RoleFilter`의 `RULES`에 더한다. 서비스는 역할 경로를 검사하지 않는다
- 역할 검사(`auth/RoleFilter`)는 원래 요청 경로로 한다. 경로를 바꾸는 라우트 필터(`RewritePath` · `StripPrefix` 등)로 `/api/v1/admin` · `/api/v1/seller`에 닿게 하는 라우트를 만들지 않는다(바뀐 경로는 역할 검사를 받지 않는다)
- 라우트 추가: `application.yml`의 `routes`에 더한다. 검색처럼 kidly-app이 아닌 서비스의 경로는 `kidly-app` 라우트(`/**`) 위에 둔다(위에서부터 맞춘다)

## 서버 배포 (`deploy/`)
- dev · prod(#638). compose 서비스 `gateway`(profile `gateway`), 서버 `127.0.0.1:8083`(dev) · `127.0.0.1:8084`(prod)(`GATEWAY_PORT`), compose 네트워크로 `http://app:8080`에 넘긴다
- 상품 목록은 `KIDLY_SEARCH_URI`로 넘긴다. dev · prod는 `.env.dev` · `.env.prod`에 `http://search-service:8080`을 둔다(profile `search`도 켜져 있어야 한다). 기본값도 `http://search-service:8080`이고 kidly-app에는 상품 목록 API가 없다(#791)
- 자녀 프로필은 `KIDLY_CHILDPROFILE_URI`로 넘긴다. 기본값은 `http://childprofile-service:8080`이고 kidly-app에는 자녀 API가 없다. profile `childprofile`이 꺼진 환경에서는 자녀 API가 실패한다
- 추천은 `KIDLY_RECOMMEND_URI`로 넘긴다. 기본값은 `http://recommend-service:8080`이고 kidly-app에는 추천 API가 없다(#704). profile `recommend`가 꺼진 환경에서는 추천 API가 실패한다
- 정산은 `KIDLY_SETTLEMENT_URI`로 넘긴다. 기본값은 `http://settlement-service:8080`이다. kidly-app에는 정산 API가 없어 정산 API를 쓰는 환경(dev · prod)은 모두 profile `settlement`를 켠다(#729)
- 결제는 `KIDLY_PAYMENT_URI`로 넘긴다. 기본값은 `http://payment-service:8080`이고 kidly-app에는 결제 API가 없다(#525). profile `payment`가 꺼진 환경에서는 결제 API가 실패한다. 토스 웹훅(`/api/v1/webhooks/toss`)은 토큰 없이 오는 공개 API라 그대로 넘긴다
- 상품은 `KIDLY_PRODUCT_URI`로 넘긴다. 서비스를 켠 환경은 `.env`에 `http://product-service:8080`(profile `product`도 켜져 있어야 한다), 값이 없으면 기본값 `http://app:8080`이다(#791). 지금은 dev에서만 켰다. kidly-app이 아직 상품 도메인을 가지고 있어 이 값만 지우면 앱으로 되돌아간다
- dev · prod 모두 서비스(kidly-app · search-service · childprofile-service · recommend-service · settlement-service · member-service · payment-service · product-service)는 JWT를 보지 않고 Gateway가 붙인 `X-Member-*`로 로그인 회원을 만든다. 서비스는 Gateway 뒤에서만 열려야 한다
- nginx dev · 운영 블록의 `location /`만 Gateway로 간다. `/actuator/health`는 앱(dev 8081 · prod 8080)을 그대로 본다. nginx 설정은 배포로 바뀌지 않는다(`deploy/nginx/README.md`)

## 로컬 실행
- `docker compose up -d` 뒤 앱(`:apps:kidly-app:bootRun`)과 Gateway(`:apps:gateway:bootRun`, 8083)를 루트에서 띄운다. IDE는 `Kidly 앱 + Gateway` 실행 구성. 프론트 · Swagger는 8083으로 붙는다(Swagger의 '자녀 프로필' 문서는 자녀 프로필 서비스가, '추천' 문서는 추천 서비스가, '정산' 문서는 정산 서비스가 떠 있어야 열린다)
- 자녀 API(`/api/v1/children/**`)는 자녀 프로필 서비스(`:apps:childprofile-service:bootRun`, 8084)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_CHILDPROFILE_URI`를 준다
- 추천 API(`/api/v1/recommendations/**`)는 추천 서비스(`:apps:recommend-service:bootRun`, 8086)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_RECOMMEND_URI`를 준다
- 상품 목록(`GET /api/v1/products`)은 검색 서비스(`:apps:search-service:bootRun`, 8082, Elasticsearch 필요)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_SEARCH_URI`를 준다
- 정산 API(`/api/v1/seller/settlement*` · `/api/v1/admin/settlement*`)는 정산 서비스(`:apps:settlement-service:bootRun`, 8088)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_SETTLEMENT_URI`를 준다
- 회원 · 인증 · 판매자 API는 회원 서비스(`:apps:member-service:bootRun`, 8081)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_MEMBER_URI`를 준다
- 결제 API(`/api/v1/payments/**` · `/api/v1/wallets/**` 등)는 결제 서비스(`:apps:payment-service:bootRun`, 8087)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_PAYMENT_URI`를 준다
- 상품 API는 기본으로 kidly-app이 받는다. 상품 서비스(`:apps:product-service:bootRun`, 8089)로 볼 때만 `KIDLY_PRODUCT_URI=http://localhost:8089`를 준다
