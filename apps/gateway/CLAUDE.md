# Gateway (#608)

Traefik 뒤에서 모든 API 요청을 먼저 받는 실행 모듈(Spring Cloud Gateway, WebFlux). 업무 로직 · DB가 없다.

## 하는 일
요청이 거치는 순서다. 자세한 동작과 이유는 각 클래스 javadoc에 있다.
1. 보안 응답 헤더(`web/WebFilterConfig`): 모든 응답(400 포함)에 Spring Security 기본 보안 헤더를 붙인다
2. 요청 ID(`web/RequestIdFilter`): 요청마다 `X-Request-Id`를 새로 만들어 클라이언트가 보낸 값을 덮어쓰고 응답 헤더로도 돌려준다. Gateway 로그(`web/RequestFirewallFilter` · `web/GatewayErrorWebExceptionHandler`)에는 key-value `traceId`로 남긴다. 서비스(`RequestLoggingFilter`)는 같은 헤더 값을 MDC `traceId`로 써서, 값 하나로 Gateway 로그부터 서비스 로그까지 한 요청을 찾는다(#683)
3. 경로 방화벽(`web/RequestFirewallFilter`): 정규화되지 않거나 위험한 경로 · 메서드를 400으로 막는다
4. 토큰 검사(`auth/AccessTokenFilter`): 밖에서 온 `X-Member-*` · `X-Admin-*` · `X-Child-*`를 지우고, Bearer 토큰을 검증 · 무효화 확인(Redis, 아래 '무효화 목록')해 주체별 헤더를 붙인다. 실패하면 401
5. 역할 검사(`auth/RoleFilter`): `/api/v1/admin/**`는 관리자 주체, `/api/v1/seller/**`는 SELLER 회원 주체, `/api/v1/child/**`는 자녀 주체. 없으면 401 · 403
6. 요청 수 제한: `ratelimit/GlobalRateLimitFilter`는 모든 요청을 주체 ID 또는 비로그인 IP마다 Redis에 센다(#1417, 아래 '전체 요청 수 제한'). 검색어가 있는 `GET /api/v1/products`는 `SearchRateLimitFilter`가 따로 센다(#816, 분당 30번). 챗봇 질문 `POST /api/v1/chatbot/messages`는 `ChatRateLimitFilter`가 따로 센다(#1191, 분당 10번). 검색 · 챗봇 제한은 OpenAI 비용을 막으려는 것이며 Redis에 두어 Gateway 여러 대가 한도를 공유한다. 키별 · 전체 한도는 관리자 API로 런타임에 바꾸고 검색 · 챗봇 한도는 배포로만 바꾼다(#1421). IP는 Traefik이 덮어쓰는 `X-Real-IP`, 없으면 연결 주소다. `X-Forwarded-For`는 쓰지 않는다. Traefik은 클라이언트가 보낸 값을 지우고 클라이언트 IP 하나를 넣는다
7. CORS(`application.yml`의 `globalcors`) · 라우팅. 서비스는 CORS를 하지 않는다
- 1 · 2 · 3은 WebFilter(`web/WebFilterConfig`에 순서대로 등록), 4 · 5 · 6은 GlobalFilter(`RoleFilter` 순서 = `AccessTokenFilter.ORDER + 1`, 전체 · 검색 · 챗봇 제한 필터 = `+ 2`)
- 400 · 401 · 403 · 404 · 429 · 500 · 503 · 504 본문은 `web/ProblemResponse`가 쓴다(#668)
  - 404(`/internal/**`, 라우트에 없는 경로): 이름 있는 필터 `web/NotFoundGatewayFilterFactory`(`application.yml`에서 `NotFound`)
  - 503 · 504 · 500: `web/GatewayErrorWebExceptionHandler`
  - 백엔드 호출 timeout(`application.yml`의 `spring.cloud.gateway.server.webflux.httpclient`): 연결 3초, 응답 55초. 55초는 결제 승인(Toss 연결 3초 + 읽기 30초)보다 길다. Traefik은 기본 응답 시간 제한이 없다. 테스트는 `application-test.yml`에서 응답 1초로 줄인다
  - 추천 라우트만 응답 3초(라우트 `metadata.response-timeout`, #704). 추천이 느려도 화면이 추천 영역만 비우고 넘어가게 한다. AI 답을 기다리는 추천 API는 추천 서비스가 기다리는 시간보다 0.5초 길게 따로 라우트를 둔다: 함께 사면 `/api/v1/recommendations/companions` 4.5초, 사이즈 `/api/v1/recommendations/size` 4초, 맛보기 `/api/v1/recommendations/products/preview` 3.5초(#1155). 함께 사면 · 사이즈 · 맛보기를 스트림(`Accept: text/event-stream`)으로 부르면 규칙 결과 뒤에 AI 결과가 최대 10초 뒤에 오므로, 이 요청만 따로 라우트를 두어 15초로 둔다(응답 조각 사이 간격으로 잰다). 테스트는 전체 1초 설정과 상관없이 라우트 값을 쓴다(`BackendTimeoutIntegrationTest`)
- 라우트: `application.yml`로만. `/internal/**`은 404, `GET /api/v1/products`(상품 목록, 경로가 정확히 같을 때만) · `POST /api/v1/search/clicks`(검색 결과 클릭 기록, #1004)는 `kidly.gateway.search-uri`(#526), `/api/v1/children` · `/api/v1/children/**`(자녀 프로필)는 `kidly.gateway.member-uri`(#809), `/api/v1/recommendations/**`(추천)는 `kidly.gateway.recommend-uri`(#704), `/api/v1/seller/settlement-targets` · `/api/v1/seller/settlements[/**]` · `/api/v1/admin/settlements[/**]` · `/api/v1/admin/settlement-runs/**` · `/api/v1/admin/settlement-dead-letters/**`(정산)는 `kidly.gateway.settlement-uri`(#729), 회원 · 인증 · 판매자(`/api/v1/auth/**` · `/api/v1/members/**` · `/api/v1/seller/me/**` · `/api/v1/admin/admins/**` · `/api/v1/admin/members/**` · `/api/v1/admin/sellers/**` · `/api/v1/admin/seller-applications/**` · `/api/v1/dev/**`)는 `kidly.gateway.member-uri`, 개발용 예치금 충전(`/api/v1/dev/wallets/**`, local · test · dev 전용, #1010)은 회원의 `/api/v1/dev/**`보다 먼저 맞춰 `kidly.gateway.payment-uri`, `/api/v1/payments/**` · `/api/v1/wallets/**` · `/api/v1/webhooks/toss` · `/api/v1/admin/payments/**` · `/api/v1/admin/wallets/**`(결제 · 지갑 · 충전 · 토스 웹훅 · 결제 관리자)는 `kidly.gateway.payment-uri`(#525, 응답 제한은 전체 55초), `/api/v1/products/**` · `/api/v1/categories/**` · `/api/v1/seller/products/**` · `/api/v1/seller/product-images/**` · `/api/v1/seller/options/**` · `/api/v1/admin/products/**` · `/api/v1/admin/categories/**` · `/api/v1/admin/product-types/**` · `/api/v1/admin/attributes/**` · `/api/v1/admin/measurement-items/**`(상품 · 카테고리 · 판매자 · 관리자 상품 API)는 `kidly.gateway.product-uri`(#791, 상품 목록 `GET /api/v1/products`는 위의 검색 라우트가 먼저 잡는다), `/api/v1/orders/**` · `/api/v1/cart/**` · `/api/v1/wish/**` · `/api/v1/seller/orders/**` · `/api/v1/admin/orders/**`(주문 · 장바구니 · 조르기 · 판매자 주문 · 주문 관리자 API)는 `kidly.gateway.order-uri`, `/api/v1/reviews/**` · `/api/v1/favorites/**` · `/api/v1/admin/community/**`(리뷰 · 찜 · 커뮤니티 관리자 API)는 `kidly.gateway.community-uri`, `/api/v1/notifications/**`(알림함)는 `kidly.gateway.notification-uri`(#852), `/api/v1/chatbot/**` · `/api/v1/admin/chatbot/**`(챗봇 · 챗봇 관리자 API)는 `kidly.gateway.chatbot-uri`(#1191), 나머지는 맨 끝 `not-found` 라우트(`/**`)가 404. 라우트가 있어야 인증 · 역할 검사(GlobalFilter)가 돌므로 이 라우트를 지우지 않는다
- Traefik이 넣은 `Host` · `X-Forwarded-*`는 덧붙이지 않고 그대로 넘긴다(`PreserveHostHeader`, `trusted-proxies`, `x-forwarded.*-enabled: false`). Gateway 자신은 `server.forward-headers-strategy: native`로 https · 원래 호스트로 본다(같은 출처 판단)

## 전체 요청 수 제한
- Redis 해시 `kidly:gateway:rate-limit:client:{RateLimitKeys.keyOf 결과}`에 남은 토큰과 갱신 시각을 저장한다. 키는 `MEMBER:123`, `ADMIN:123`, `CHILD:123`, `IP:주소`처럼 주체 종류와 ID 또는 IP를 포함한다. Lua가 Redis 시각으로 토큰을 채우고 차감하므로 Gateway 여러 대가 같은 한도를 쓴다. 기본 한도는 키마다 분당 200개이며 한꺼번에 200개까지 보낼 수 있다. 넘으면 429와 `Retry-After`(초)를 낸다. 전체 버킷 키 `kidly:gateway:rate-limit:total`로 모든 Gateway를 합쳐 초당 500개까지 받으며 전체 한도를 넘으면 503과 `Retry-After`를 낸다. 두 버킷은 Lua 하나로 확인하고 둘 다 남았을 때만 차감한다. 해시 TTL은 한도가 다 차는 시간이다
- 관리자 `GET /api/v1/admin/gateway/rate-limit`으로 지금 적용 중인 설정(`{"enabled":true,"client":{"capacity":200,"period":"PT1M"},"total":{"capacity":500,"period":"PT1S"}}`)을 읽고, `PUT`으로 보낸 칸만 바꾼다(#1421). 예: `{"enabled":false}`, `{"total":{"capacity":800,"period":"PT1S"}}`. period는 ISO-8601이다. 본문이 없거나 바꿀 칸이 없거나 capacity 1 미만 · period 1ms 미만이면 400이다. 이 경로는 전체 한도에서 제외한다. 다른 Gateway는 기본 2초마다 Redis를 읽어 설정을 갱신한다. 끄면 키별 · 전체 한도가 꺼지며 검색 · 챗봇 제한에는 영향이 없다. 검색 · 챗봇 한도는 OpenAI 비용과 묶여 있어 배포로만 바꾼다
- 설정 해시 `kidly:gateway:rate-limit:settings`(필드 `enabled`, `client-capacity`, `client-period-ms`, `total-capacity`, `total-period-ms`)는 TTL이 없다. `enabled`는 정확히 "false"일 때만 꺼지고 없으면 켜진다. 한도는 두 필드가 모두 맞는 값일 때만 쓰고 아니면 yml 기본값이다. 해시 값이 yml보다 우선하므로 yml 기본값을 바꿔 배포해도 해시에 값이 남아 있으면 해시가 이긴다. 되돌리려면 그 필드를 지우거나 PUT으로 다시 쓴다. Redis 읽기 실패 시 마지막 설정을 유지한다. 검색 · 챗봇도 각각 search · chat 이름의 Redis 버킷을 쓴다. 버킷 확인 실패 시 검색 · 챗봇을 포함해 요청을 통과시키고 ERROR 로그를 남긴다. 설정 쓰기 실패 시 503이다

## 무효화 목록
- `revocation/AccessTokenRevocations`: Redis 해시 `kidly:gateway:revocations`(필드 = 토큰 주체 `PrincipalId.toSubject()`, 값 = 무효화 시각 epoch 초, 키 TTL 없음, #1327). 그 시각까지(초 단위, 같은 초 포함) 발급된 토큰은 401. Gateway가 여러 대여도, 다시 떠도 같은 목록을 본다
- 쓰기는 Lua 하나로 더 늦은 시각만 남기고 보존 기간(31분 = Access Token 유효 기간 30분 + 시계 오차 60초)이 지난 필드를 지운다. 같은 메시지를 여러 번 · 여러 대가 처리해도 결과가 같다
- 확인은 `auth/GatewayJwtConfig`가 디코더를 감싸 요청마다 Redis를 한 번 읽는다. 무효화면 `BadJwtException`이라 `auth/AccessTokenFilter`가 401을 낸다
- Redis 장애(연결 실패 · 명령 제한 시간 300ms 초과 등)면 확인 없이 통과시키고 ERROR 로그를 남긴다(토큰 · 주체 없이). 거절하면 Redis 장애가 로그인한 모든 요청의 장애가 된다. 놓친 토큰도 최대 30분 뒤 만료된다. 그래서 Redis 헬스는 전체 health에서 뺀다(`management.health.redis.enabled: false`)
- Redis는 추천 AI 답 캐시와 같이 쓴다(64MB, `volatile-lru`). TTL 있는 추천 답과 전체 요청 수 버킷은 지워질 수 있고 TTL 없는 무효화 목록 · 표식 · 요청 수 제한 설정은 남는다
- `kidly.auth.events`의 `auth.tokens-revoked`를 받아 채운다(발행은 회원 서비스의 인증 모듈, `apps/member-service/auth`). 인증은 업무 트랜잭션에 아웃박스를 저장하고 200ms 주기 릴레이로 보낸다. 구독 그룹 `kidly.gateway`, 다른 종류는 건너뛴다
- 파티션마다 TTL 없는 표식 `kidly:gateway:revocations:loaded:{파티션}`: '이 파티션의 최근 31분 무효화가 Redis에 들어 있다'. 표식이 있으면 구독 그룹 책갈피대로 읽고, 없으면(처음 배포, Redis 재시작) 그 파티션만 31분 전으로 되감아 다시 읽고 표식을 쓴다(`revocation/AuthTokensRevokedListener`)
  - 파티션을 받을 때와 컨테이너 idle 이벤트(1초 간격, 첫 메시지 전에는 5초)마다 확인한다. 돌던 중 Redis가 다시 떠 비면 다음 idle 확인이 되감는다. 그 사이(최대 약 1초 + 다시 읽는 시간) 무효화된 토큰이 통과할 수 있다
  - 파티션을 받을 때 표식을 읽지 못하면 받은 파티션을 모두 되감는다(표식은 쓰지 못하므로 Redis가 돌아오면 idle 확인이 다시 되감는다). idle 확인에서 읽지 못하면 아무것도 하지 않는다
  - 표식이 있어 뜰 때 되감지 않으므로 다시 읽기를 기다리지 않고 바로 요청을 받는다. 롤링 배포에서 옛 · 새 Pod가 파티션을 나눠 가져도 같은 Redis를 보므로 공백이 없다
- 읽기 실패(JSON · `eventType` 헤더 · data 없음, 모르는 주체 종류 등)는 재시도 없이 DLT `kidly.auth.events.gateway.dlt` + ERROR 로그(본문 없이 `eventType` · `eventId` · 키). 표식이 없어 되감으면 31분 안에서 DLT로 간 메시지는 다시 읽혀 DLT로 다시 간다(ERROR 로그도 다시)
- Redis 쓰기 실패(`DataAccessException`)는 DLT로 보내면 무효화를 잃으므로 5초 간격으로 끝없이 다시 시도한다(시도마다 WARN). 그동안 리스너는 뒤 메시지를 읽지 않는다
- `jwt.access-token-validity`는 회원 서비스 인증 모듈의 `auth.yml`(`apps/member-service/auth/src/main/resources`)과 같아야 한다
- 한계
  - seek과 표식 뒤 되감은 메시지를 처리 · 커밋하기 전에 Gateway가 죽거나 리밸런스되면(롤링 배포 · 인스턴스 추가 포함) 책갈피가 되감기 전에 남아 그 파티션의 최근 무효화를 놓친다. Redis 재시작과 겹칠 때만
  - 무효화 메시지가 계속 와 idle 이벤트가 나지 않으면 Redis 재시작을 늦게 알아챈다
  - Redis가 64MB를 TTL 없는 키로 채우면(volatile-lru가 지울 키가 없으면) 쓰기가 실패해 리스너가 다시 시도하며 멈춘다

## 지켜야 할 것
- CORS 값(허용 origin · 메서드 · 노출 헤더)은 Gateway(`application.yml`)에서만 바꾼다
- 주체 헤더 이름 · 접두어는 `PrincipalHeaders`, 토큰 주체는 `PrincipalId`에 있다. 회원 헤더 이름 · 역할 목록(쉼표) · 닉네임 인코딩(UTF-8 퍼센트 인코딩, 공백 `%20`) · 회원 ID 해석은 `MemberHeaders`, 역할은 `Role`, 토큰 클레임 이름은 `AccessTokenClaims`에 있다(모두 `shared`의 `com.bbcc.kidly.shared.auth`). Gateway · `common` · 회원이 같은 코드를 쓰므로 한 곳만 고친다
- 400 · 401 · 403 · 404 · 500 본문은 서비스와 같아야 한다(문구 `common` `CommonErrorCode`의 `INVALID_INPUT` · `UNAUTHORIZED` · `FORBIDDEN` · `NOT_FOUND` · `INTERNAL_ERROR`, 5필드와 오류 코드 `code`(같은 상수 이름) `ProblemDetails`). 모듈이 달라 테스트로 묶지 못하므로 한쪽을 바꾸면 `web/ProblemResponse`도 바꾼다
- 로그아웃(`/api/v1/auth/logout`)은 Refresh Token 쿠키로만 인증하므로 Access Token을 검증하지 않고 주체 헤더 없이 넘긴다. 탈퇴로 무효화된 토큰이 함께 와도 쿠키를 지울 수 있어야 한다(`auth/AccessTokenFilter`)
- 역할이 필요한 경로를 더하면 `auth/RoleFilter`의 `RULES`에 더한다. 서비스는 역할 경로를 검사하지 않는다
- 역할 검사(`auth/RoleFilter`)는 원래 요청 경로로 한다. 경로를 바꾸는 라우트 필터(`RewritePath` · `StripPrefix` 등)로 `/api/v1/admin` · `/api/v1/seller`에 닿게 하는 라우트를 만들지 않는다(바뀐 경로는 역할 검사를 받지 않는다)
- 라우트 추가: `application.yml`의 `routes`에 더한다. `not-found` 라우트(`/**`) 위에 두고, 경로가 겹치면 구체적인 라우트를 먼저 둔다(위에서부터 맞춘다)

## 서버 배포 (`deploy/`)
- dev · prod(#638). compose 서비스 `gateway`(profile `gateway`), 서버 `127.0.0.1:8083`(dev) · `127.0.0.1:8084`(prod)(`GATEWAY_PORT`), compose 네트워크로 각 서비스에 넘긴다
- 무효화 목록은 `SPRING_DATA_REDIS_HOST: redis`(같은 namespace 의 Redis)에 둔다. Gateway 는 Redis 를 늘 쓴다. `.env.dev` 의 `K3S_PROFILES` 에서 `redis` 를 빼지 않는다(빼면 무효화를 확인하지 못하고 통과시키며 요청마다 ERROR, 리스너는 Redis 에 쓰지 못해 다시 시도하며 멈춘다)
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
- Traefik(t3.large k3s 의 Pod, #1423)은 api 호스트의 모든 요청을 Gateway로 넘긴다. 주체 헤더 제거와 actuator 차단은 Gateway가 한다. Traefik 설정은 앱 배포로 바뀌지 않는다(`deploy/traefik/README.md`). Traefik은 외부의 `X-Forwarded-*`를 지우고 다시 넣는다. Gateway는 ClusterIP이며, native가 원격 주소를 `X-Forwarded-For`로 바꾼 뒤 `trusted-proxies`를 검사하므로 프록시 주소로 좁힐 수 없다
- actuator(health · prometheus)는 management 포트 8085에만 연다(#1404). 공개 포트의 `/actuator/**`는 not-found 라우트가 404다. k3s probe와 Alloy 지표 수집은 Pod의 8085를 본다(`deploy/k3s/k3s.py`가 compose healthcheck 포트로 probe · `prometheus.io/port` annotation을 만든다)

## 로컬 실행
- `docker compose up -d` 뒤 Gateway(`:apps:gateway:bootRun`, 8083)와 필요한 서비스를 띄운다. Swagger는 각 서비스에서 확인한다. Gateway actuator는 `localhost:8085`다
- 무효화 목록은 `docker compose up -d`가 띄우는 Redis(localhost:6379)에 둔다
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
