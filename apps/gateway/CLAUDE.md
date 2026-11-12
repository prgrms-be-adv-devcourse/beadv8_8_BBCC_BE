# Gateway (#608)

nginx 뒤에서 모든 API 요청을 먼저 받는 실행 모듈(Spring Cloud Gateway, WebFlux). 업무 로직 · DB가 없다.

## 하는 일
요청이 거치는 순서다. 자세한 동작과 이유는 각 클래스 javadoc에 있다.
1. 보안 응답 헤더(`web/WebFilterConfig`): 모든 응답(400 포함)에 Spring Security 기본 보안 헤더를 붙인다
2. 요청 ID(`web/RequestIdFilter`): 요청마다 `X-Request-Id`를 새로 만들어 클라이언트가 보낸 값을 덮어쓰고 응답 헤더로도 돌려준다. Gateway 로그(`web/RequestFirewallFilter` · `web/GatewayErrorWebExceptionHandler`)에는 key-value `traceId`로 남긴다. 서비스(`RequestLoggingFilter`)는 같은 헤더 값을 MDC `traceId`로 써서, 값 하나로 Gateway 로그부터 서비스 로그까지 한 요청을 찾는다(#683)
3. 경로 방화벽(`web/RequestFirewallFilter`): 정규화되지 않거나 위험한 경로 · 메서드를 400으로 막는다
4. 토큰 검사(`auth/AccessTokenFilter`): 밖에서 온 `X-Member-*` · `X-Admin-*` · `X-Child-*`를 지우고, Bearer 토큰을 검증 · 무효화 확인해 주체별 헤더를 붙인다. 실패하면 401
5. 역할 검사(`auth/RoleFilter`): `/api/v1/admin/**`는 관리자 주체, `/api/v1/seller/**`는 SELLER 회원 주체, `/api/v1/child/**`는 자녀 주체. 없으면 401 · 403
6. 검색 요청 수 제한(`ratelimit/SearchRateLimitFilter`, #816): 검색어가 있는 `GET /api/v1/products`만 주체(회원 · 관리자 · 자녀) ID, 비로그인은 IP(nginx가 덮어쓰는 `X-Real-IP`, 없으면 연결 주소)마다 센다. 한도는 `application.yml`의 `kidly.gateway.rate-limit.search`(분당 30번). 넘으면 429 + `Retry-After`(초). 검색 서비스가 검색어마다 임베딩 API(OpenAI)를 부르기 때문이다. Gateway 메모리라 다시 뜨면 비고, Gateway를 여러 대로 늘리면 공용 저장소(#690)로 옮긴다. `X-Forwarded-For` 첫 값은 클라이언트가 지어낼 수 있어 쓰지 않는다. 챗봇 질문 `POST /api/v1/chatbot/messages`도 같은 방식으로 `ChatRateLimitFilter`가 센다(`kidly.gateway.rate-limit.chat`, 분당 10번, OpenAI 비용 방어, #1191. 관리자 챗봇 경로는 세지 않는다)
7. CORS(`application.yml`의 `globalcors`) · 라우팅. 서비스는 CORS를 하지 않는다
- 1 · 2 · 3은 WebFilter(`web/WebFilterConfig`에 순서대로 등록), 4 · 5 · 6은 GlobalFilter(`RoleFilter` 순서 = `AccessTokenFilter.ORDER + 1`, `SearchRateLimitFilter` · `ChatRateLimitFilter` = `+ 2`)
- 400 · 401 · 403 · 404 · 429 · 500 · 503 · 504 본문은 `web/ProblemResponse`가 쓴다(#668)
  - 404(`/internal/**`, 라우트에 없는 경로): 이름 있는 필터 `web/NotFoundGatewayFilterFactory`(`application.yml`에서 `NotFound`)
  - 503 · 504 · 500: `web/GatewayErrorWebExceptionHandler`
  - 백엔드 호출 timeout(`application.yml`의 `spring.cloud.gateway.server.webflux.httpclient`): 연결 3초, 응답 55초. 55초는 결제 승인(Toss 연결 3초 + 읽기 30초)보다 길고 nginx 기본 `proxy_read_timeout` 60초보다 짧다. 테스트는 `application-test.yml`에서 응답 1초로 줄인다
  - 추천 라우트만 응답 3초(라우트 `metadata.response-timeout`, #704). 추천이 느려도 화면이 추천 영역만 비우고 넘어가게 한다. AI 답을 기다리는 추천 API는 추천 서비스가 기다리는 시간보다 0.5초 길게 따로 라우트를 둔다: 함께 사면 `/api/v1/recommendations/companions` 4.5초, 사이즈 `/api/v1/recommendations/size` 4초, 맛보기 `/api/v1/recommendations/products/preview` 3.5초(#1155). 함께 사면 · 맛보기를 스트림(`Accept: text/event-stream`)으로 부르면 규칙 결과 뒤에 AI 결과가 최대 10초 뒤에 오므로, 이 요청만 따로 라우트를 두어 15초로 둔다(응답 조각 사이 간격으로 잰다). 테스트는 전체 1초 설정과 상관없이 라우트 값을 쓴다(`BackendTimeoutIntegrationTest`)
- 라우트: `application.yml`로만. `/internal/**`은 404, `GET /api/v1/products`(상품 목록, 경로가 정확히 같을 때만) · `POST /api/v1/search/clicks`(검색 결과 클릭 기록, #1004)는 `kidly.gateway.search-uri`(#526), `/api/v1/children` · `/api/v1/children/**`(자녀 프로필)는 `kidly.gateway.member-uri`(#809), `/api/v1/recommendations/**`(추천)는 `kidly.gateway.recommend-uri`(#704), `/api/v1/seller/settlement-targets` · `/api/v1/seller/settlements[/**]` · `/api/v1/admin/settlements[/**]` · `/api/v1/admin/settlement-runs/**` · `/api/v1/admin/settlement-dead-letters/**`(정산)는 `kidly.gateway.settlement-uri`(#729), 회원 · 인증 · 판매자(`/api/v1/auth/**` · `/api/v1/members/**` · `/api/v1/seller/me/**` · `/api/v1/admin/admins/**` · `/api/v1/admin/members/**` · `/api/v1/admin/sellers/**` · `/api/v1/admin/seller-applications/**` · `/api/v1/dev/**`)는 `kidly.gateway.member-uri`, 개발용 예치금 충전(`/api/v1/dev/wallets/**`, local · test · dev 전용, #1010)은 회원의 `/api/v1/dev/**`보다 먼저 맞춰 `kidly.gateway.payment-uri`, `/api/v1/payments/**` · `/api/v1/wallets/**` · `/api/v1/webhooks/toss` · `/api/v1/admin/payments/**` · `/api/v1/admin/wallets/**`(결제 · 지갑 · 충전 · 토스 웹훅 · 결제 관리자)는 `kidly.gateway.payment-uri`(#525, 응답 제한은 전체 55초), `/api/v1/products/**` · `/api/v1/categories/**` · `/api/v1/seller/products/**` · `/api/v1/seller/product-images/**` · `/api/v1/seller/options/**` · `/api/v1/admin/products/**` · `/api/v1/admin/categories/**` · `/api/v1/admin/product-types/**` · `/api/v1/admin/attributes/**` · `/api/v1/admin/measurement-items/**`(상품 · 카테고리 · 판매자 · 관리자 상품 API)는 `kidly.gateway.product-uri`(#791, 상품 목록 `GET /api/v1/products`는 위의 검색 라우트가 먼저 잡는다), `/api/v1/orders/**` · `/api/v1/cart/**` · `/api/v1/wish/**` · `/api/v1/seller/orders/**` · `/api/v1/admin/orders/**`(주문 · 장바구니 · 조르기 · 판매자 주문 · 주문 관리자 API)는 `kidly.gateway.order-uri`, `/api/v1/reviews/**` · `/api/v1/favorites/**` · `/api/v1/admin/community/**`(리뷰 · 찜 · 커뮤니티 관리자 API)는 `kidly.gateway.community-uri`, `/api/v1/notifications/**`(알림함)는 `kidly.gateway.notification-uri`(#852), `/api/v1/chatbot/**` · `/api/v1/admin/chatbot/**`(챗봇 · 챗봇 관리자 API)는 `kidly.gateway.chatbot-uri`(#1191), 나머지는 맨 끝 `not-found` 라우트(`/**`)가 404. 라우트가 있어야 인증 · 역할 검사(GlobalFilter)가 돌므로 이 라우트를 지우지 않는다
- nginx가 넣은 `Host` · `X-Forwarded-*`는 덧붙이지 않고 그대로 넘긴다(`PreserveHostHeader`, `trusted-proxies`, `x-forwarded.*-enabled: false`). Gateway 자신은 `server.forward-headers-strategy: native`로 https · 원래 호스트로 본다(같은 출처 판단)

## 무효화 목록
- `revocation/AccessTokenRevocations`: `PrincipalId`(주체 종류, 주체 ID) → 무효화 시각, 메모리. 그 시각까지(초 단위, 같은 초 포함) 발급된 토큰은 401
- `kidly.auth.events`의 `auth.tokens-revoked`를 받아 채운다(발행은 회원 서비스의 인증 모듈, `apps/member-service/auth`). 구독 그룹 `kidly.gateway`, 다른 종류는 건너뛴다
- 메모리라 다시 뜨면 빈다. 그래서 파티션을 받을 때마다 31분 전(Access Token 유효 기간 30분 + 시계 오차 60초)으로 되감아 다시 읽는다. 구독 그룹의 책갈피로 시작 위치를 정하지 않는다(31분 안에 메시지가 없는 파티션만 책갈피 위치에서 읽는다. 거기 있는 메시지는 보존 기간이 지나 결과가 같다)
- 읽기 실패는 재시도 없이 DLT `kidly.auth.events.gateway.dlt` + ERROR 로그(본문 없이 `eventType` · `eventId` · 키)
- 31분 안에서 읽지 못해 DLT로 간 메시지는 Gateway가 다시 뜰 때마다 다시 읽혀 DLT로 다시 간다(ERROR 로그도 다시)
- `jwt.access-token-validity`는 회원 서비스 인증 모듈의 `auth.yml`(`apps/member-service/auth/src/main/resources`)과 같아야 한다
- 다시 다 읽기 전에는 요청을 받지 않는다(`revocation/RevocationReplay`, #1176): readiness가 REFUSING_TRAFFIC이라 k3s가 새 Pod로 보내지 않는다(`revocation/RevocationAwareApplicationAvailability`가 Boot의 ApplicationAvailability를 대신한다). liveness는 그대로다
  - 다 읽음 = 무효화 리스너가 파티션을 받은 뒤 더 읽을 메시지가 없을 때(컨테이너 idle 이벤트, 1초 간격 · 첫 메시지 전에는 5초)
  - 리스너가 다시 시작하면 다시 기다린다. 한 번 다 읽은 뒤의 재조정(rebalance)으로는 다시 막지 않는다(받은 무효화는 메모리에 그대로 있다)
  - 시간 상한 60초: 넘으면 WARN을 남기고 요청을 받는다(브로커 장애로 Gateway 전체가 멈추지 않게). 앱이 뜰 때 리스너가 돌지 않으면(테스트의 auto-startup: false) 기다리지 않는다
- 한계: 시간 상한을 넘겨 Ready가 된 경우, 그리고 롤링 배포에서 옛 Pod · 새 Pod가 겹칠 때는 무효화된 토큰이 통과할 수 있다. 같은 구독 그룹이라 새 Pod는 처음에 파티션 일부만 받아 그것만 다 읽고 Ready가 되고, 옛 Pod가 내려간 뒤 나머지 파티션을 받아 다시 읽는 몇 초 동안 그 파티션의 무효화를 모른다. 없애려면 인스턴스마다 모든 파티션을 직접 받아야 한다(구독 그룹 규칙과 함께 정할 일)

## 지켜야 할 것
- CORS 값(허용 origin · 메서드 · 노출 헤더)은 Gateway(`application.yml`)에서만 바꾼다
- 주체 헤더 이름 · 접두어는 `PrincipalHeaders`, 토큰 주체는 `PrincipalId`에 있다. 회원 헤더 이름 · 역할 목록(쉼표) · 닉네임 인코딩(UTF-8 퍼센트 인코딩, 공백 `%20`) · 회원 ID 해석은 `MemberHeaders`, 역할은 `Role`, 토큰 클레임 이름은 `AccessTokenClaims`에 있다(모두 `shared`의 `com.bbcc.kidly.shared.auth`). Gateway · `common` · 회원이 같은 코드를 쓰므로 한 곳만 고친다
- 400 · 401 · 403 · 404 · 500 본문은 서비스와 같아야 한다(문구 `common` `CommonErrorCode`의 `INVALID_INPUT` · `UNAUTHORIZED` · `FORBIDDEN` · `NOT_FOUND` · `INTERNAL_ERROR`, 5필드 `ProblemDetails`). 모듈이 달라 테스트로 묶지 못하므로 한쪽을 바꾸면 `web/ProblemResponse`도 바꾼다
- 로그아웃(`/api/v1/auth/logout`)은 Refresh Token 쿠키로만 인증하므로 Access Token을 검증하지 않고 주체 헤더 없이 넘긴다. 탈퇴로 무효화된 토큰이 함께 와도 쿠키를 지울 수 있어야 한다(`auth/AccessTokenFilter`)
- 역할이 필요한 경로를 더하면 `auth/RoleFilter`의 `RULES`에 더한다. 서비스는 역할 경로를 검사하지 않는다
- 역할 검사(`auth/RoleFilter`)는 원래 요청 경로로 한다. 경로를 바꾸는 라우트 필터(`RewritePath` · `StripPrefix` 등)로 `/api/v1/admin` · `/api/v1/seller`에 닿게 하는 라우트를 만들지 않는다(바뀐 경로는 역할 검사를 받지 않는다)
- 라우트 추가: `application.yml`의 `routes`에 더한다. `not-found` 라우트(`/**`) 위에 두고, 경로가 겹치면 구체적인 라우트를 먼저 둔다(위에서부터 맞춘다)

## 서버 배포 (`deploy/`)
- dev · prod(#638). compose 서비스 `gateway`(profile `gateway`), 서버 `127.0.0.1:8083`(dev) · `127.0.0.1:8084`(prod)(`GATEWAY_PORT`), compose 네트워크로 각 서비스에 넘긴다
- 상품 목록은 `KIDLY_SEARCH_URI`로 넘긴다. dev는 `.env.dev`에 k3s 안 이름 `http://search:8080`을 둔다(#1142). 기본값도 `http://search-service:8080`이다
- 추천은 `KIDLY_RECOMMEND_URI`로 넘긴다. 기본값은 `http://recommend-service:8080`이다. profile `recommend`가 꺼진 환경에서는 추천 API가 실패한다
- 정산은 `KIDLY_SETTLEMENT_URI`로 넘긴다. 기본값은 `http://settlement-service:8080`이다. 정산 API를 쓰는 환경은 profile `settlement`를 켠다(#729)
- 결제는 `KIDLY_PAYMENT_URI`로 넘긴다. 기본값은 `http://payment-service:8080`이다. profile `payment`가 꺼진 환경에서는 결제 API가 실패한다. 토스 웹훅(`/api/v1/webhooks/toss`)은 토큰 없이 오는 공개 API라 그대로 넘긴다
- 상품은 `KIDLY_PRODUCT_URI`로 넘긴다. 기본값은 `http://product-service:8080`이다. profile `product`가 꺼진 환경에서는 상품 API가 실패한다. dev · prod 모두 켰다(prod는 첫 main 배포 때 뜬다)
- 주문은 `KIDLY_ORDER_URI`로 넘긴다. 기본값은 `http://order-service:8080`이다. profile `order`가 꺼진 환경에서는 주문 API가 실패한다
- 커뮤니티는 `KIDLY_COMMUNITY_URI`로 넘긴다. 기본값은 `http://community-service:8080`이다. profile `community`가 꺼진 환경에서는 리뷰 · 찜 API가 실패한다
- 알림함은 `KIDLY_NOTIFICATION_URI`로 넘긴다. 기본값은 `http://notification-service:8080`이다. profile `notification`이 꺼진 환경에서는 알림함 API가 실패한다
- 챗봇은 `KIDLY_CHATBOT_URI`로 넘긴다. 기본값은 `http://chatbot-service:8080`이다. profile `chatbot`이 꺼진 환경에서는 챗봇 API가 실패한다(#1191)
- dev · prod 모두 서비스(search-service · recommend-service · settlement-service · member-service · payment-service · product-service · order-service · community-service · notification-service · chatbot-service)는 JWT를 보지 않고 Gateway가 붙인 `X-Member-*`로 로그인 회원을 만든다. 서비스는 Gateway 뒤에서만 열려야 한다
- nginx(t3.large k3s 의 Pod, #1309) dev 블록의 `location /`와 `/actuator/health`는 Gateway로 간다. nginx 설정은 앱 배포로 바뀌지 않는다(`deploy/nginx/README.md`)

## 로컬 실행
- `docker compose up -d` 뒤 Gateway(`:apps:gateway:bootRun`, 8083)와 필요한 서비스를 띄운다. Swagger는 각 서비스에서 확인한다.
- 추천 API(`/api/v1/recommendations/**`)는 추천 서비스(`:apps:recommend-service:bootRun`, 8086)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_RECOMMEND_URI`를 준다
- 상품 목록(`GET /api/v1/products`)은 검색 서비스(`:apps:search-service:bootRun`, 8082, Elasticsearch 필요)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_SEARCH_URI`를 준다
- 정산 API(`/api/v1/seller/settlement*` · `/api/v1/admin/settlement*`)는 정산 서비스(`:apps:settlement-service:bootRun`, 8088)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_SETTLEMENT_URI`를 준다
- 회원 · 인증 · 판매자 API는 회원 서비스(`cd apps/member-service && ./gradlew bootRun`, 8081. 루트 빌드에 없다)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_MEMBER_URI`를 준다
- 결제 API(`/api/v1/payments/**` · `/api/v1/wallets/**` 등)는 결제 서비스(`:apps:payment-service:bootRun`, 8087)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_PAYMENT_URI`를 준다
- 상품 API(`/api/v1/products/**` · `/api/v1/seller/products/**` 등)는 상품 서비스(`:apps:product-service:bootRun`, 8089)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_PRODUCT_URI`를 준다
- 주문 API는 주문 서비스(`:apps:order-service:bootRun`, 8091)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_ORDER_URI`를 준다
- 리뷰 · 찜 API는 커뮤니티 서비스(`:apps:community-service:bootRun`, 8092)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_COMMUNITY_URI`를 준다
- 알림함 API는 알림 서비스(`:apps:notification-service:bootRun`, 8090)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_NOTIFICATION_URI`를 준다
- 챗봇 API는 챗봇 서비스(`:apps:chatbot-service:bootRun`, 8093)를 띄워야 한다. 다른 곳으로 보낼 때만 `KIDLY_CHATBOT_URI`를 준다
