# Gateway (#608)

nginx 뒤에서 모든 API 요청을 먼저 받는 실행 모듈(Spring Cloud Gateway, WebFlux). 업무 로직 · DB가 없다.

## 하는 일
- 헤더 지우기: 밖에서 보낸 `X-Member-Id` · `X-Member-Roles` · `X-Member-Nickname`은 항상 지운다(`auth/AccessTokenFilter`)
- 검증: `Authorization: Bearer`(대소문자를 가리지 않는다. 서비스와 같다)가 있으면 서명 · 만료 · 무효화를 확인하고, 통과하면 `X-Member-Id` · `X-Member-Roles`를 붙여 넘긴다. 토큰에 `nickname` 클레임이 있으면 `X-Member-Nickname`도 붙인다(UTF-8 퍼센트 인코딩, 공백 `%20`). 실패하면 401. 토큰이 없거나 Bearer가 아니면 그대로 넘긴다(공개 API 판단은 서비스)
- `Authorization`은 지우지 않는다. jwt 모드 서비스(prod · 로컬)가 직접 검증한다
- CORS: 프리플라이트에 직접 답하고 Gateway가 만든 401에도 붙인다(`application.yml`의 `globalcors`). 서비스가 붙인 같은 헤더는 `DedupeResponseHeader`로 하나만 남긴다(`Vary`는 값이 여럿이라 `RETAIN_UNIQUE`로 따로). 서비스 CORS(`common`)는 jwt 모드(prod · 로컬 직접 접속)에서만 켜지고 gateway 모드(dev)에서는 꺼진다
- 라우트: `application.yml`로만. `/internal/**`은 404, `GET /api/v1/products`(상품 목록, 경로가 정확히 같을 때만)는 `kidly.gateway.search-uri`(#526), 나머지는 kidly-app
- nginx가 넣은 `Host` · `X-Forwarded-*`는 덧붙이지 않고 그대로 넘긴다(`PreserveHostHeader`, `trusted-proxies`, `x-forwarded.*-enabled: false`). Gateway 자신은 `server.forward-headers-strategy: native`로 https · 원래 호스트로 본다(같은 출처 판단)

## 무효화 목록
- `revocation/AccessTokenRevocations`: 회원 ID → 무효화 시각, 메모리. 그 시각까지(초 단위, 같은 초 포함) 발급된 토큰은 401
- `kidly.member.events`의 `member.tokens-revoked`를 받아 채운다. 구독 그룹 `kidly.gateway`, 다른 종류는 건너뛴다
- 메모리라 다시 뜨면 빈다. 그래서 파티션을 받을 때마다 31분 전(Access Token 유효 기간 30분 + 시계 오차 60초)으로 되감아 다시 읽는다. 구독 그룹의 책갈피로 시작 위치를 정하지 않는다(31분 안에 메시지가 없는 파티션만 책갈피 위치에서 읽는다. 거기 있는 메시지는 보존 기간이 지나 결과가 같다)
- 읽기 실패는 재시도 없이 DLT `kidly.member.events.gateway.dlt` + ERROR 로그(본문 없이 `eventType` · `eventId` · 키)
- 31분 안에서 읽지 못해 DLT로 간 메시지는 Gateway가 다시 뜰 때마다 다시 읽혀 DLT로 다시 간다(ERROR 로그도 다시)
- `jwt.access-token-validity`는 kidly-app `auth.yml`과 같아야 한다
- 한계: 기동 직후 되감아 다 읽기 전에는 무효화된 토큰도 통과한다. jwt 모드 서비스는 자기 무효화 확인이 한 번 더 막지만, gateway 모드 서비스(dev)는 토큰을 보지 않으므로 이 사이는 실제로 뚫린다

## 지켜야 할 것
- CORS 값(허용 origin · 메서드 · 노출 헤더)은 `common` `SecurityConfig`와 같이 바꾼다. 노출 헤더는 Gateway 값만 남는다(`DedupeResponseHeader` `RETAIN_FIRST`)
- 회원 헤더 이름과 닉네임 인코딩(UTF-8 퍼센트 인코딩, 공백 `%20`)은 `common` `GatewayHeaderAuthenticationFilter`와 같아야 한다. 모듈이 달라 테스트로 묶지 못하므로 한쪽을 바꾸면 다른 쪽도 바꾼다
- 401 본문은 `common`과 같아야 한다(문구 `CommonErrorCode.UNAUTHORIZED`, 5필드 `ProblemDetails`). 한쪽을 바꾸면 `auth/UnauthorizedResponse`도 바꾼다
- 라우트 추가: `application.yml`의 `routes`에 더한다. 검색처럼 kidly-app이 아닌 서비스의 경로는 `kidly-app` 라우트(`/**`) 위에 둔다(위에서부터 맞춘다)

## 서버 배포 (`deploy/`)
- dev · prod(#638). compose 서비스 `gateway`(profile `gateway`), 서버 `127.0.0.1:8083`(dev) · `127.0.0.1:8084`(prod)(`GATEWAY_PORT`), compose 네트워크로 `http://app:8080`에 넘긴다
- 상품 목록은 `KIDLY_SEARCH_URI`로 넘긴다. dev는 `.env.dev`에 `http://search-service:8080`(profile `search`도 켜져 있어야 한다), 값이 없는 prod는 기본값 `http://app:8080`이라 앱이 그대로 받는다
- dev의 kidly-app · search-service는 gateway 모드(`KIDLY_AUTH_MODE=gateway`, `deploy/.env.dev`)라 JWT를 다시 검증하지 않고 Gateway가 붙인 `X-Member-*`로 로그인 회원을 만든다
- prod의 kidly-app은 아직 jwt 모드라 Gateway가 검증하고도 서비스가 JWT를 다시 검증한다
- nginx dev · 운영 블록의 `location /`만 Gateway로 간다. `/actuator/health`는 앱(dev 8081 · prod 8080)을 그대로 본다. nginx 설정은 배포로 바뀌지 않는다(`deploy/nginx/README.md`)

## 로컬 실행
- `docker compose up -d` 뒤 앱(`:apps:kidly-app:bootRun`)과 Gateway(`:apps:gateway:bootRun`, 8083)를 루트에서 띄운다. IDE는 `Kidly 앱 + Gateway` 실행 구성. 프론트 · Swagger는 8083으로 붙는다
