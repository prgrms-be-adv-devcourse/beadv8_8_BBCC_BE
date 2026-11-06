# 커뮤니티 동시성 부하 측정 (#923)

리뷰 · 찜에 같은 요청이 동시에 몰려도 결과가 1회분으로 맞는지 k6로 측정한다. 로컬에서 실행하고(인증 방식은 아래), 최종 숫자는 DB가 아니라 API(요약 · 목록)로 확인해 k6 check로 남긴다.

- 로컬 전용이다. CI에서 돌리지 않고, 앱 코드 · 스키마를 바꾸지 않는다
- 이 폴더는 Gradle 소스 세트 밖이라 빌드 · 테스트에 들어가지 않는다

## 파일

| 파일 | 내용 |
|---|---|
| `seed.sql` | 준비 데이터: 상품 복사본(판매 중) 4개 · 리뷰 작성 자격(시나리오 3 · 4). 다시 돌려도 안전 |
| `cleanup.sql` | 아래 ID 범위의 찜 · 찜 수 · 리뷰 · 리뷰 집계 · 작성 자격 · 상품 복사본을 지운다. 다시 돌려도 안전 |
| `lib/common.js` | 시나리오 공통: 개발용 토큰 발급 · 동시 시작 · 요약 · 목록 확인 |
| `scenarios/1-favorite-different-members.js` | 서로 다른 회원 N명이 같은 상품을 동시에 찜 |
| `scenarios/2-favorite-same-member.js` | 같은 회원이 같은 상품을 동시에 N번 찜 |
| `scenarios/3-review-same-order-item.js` | 같은 주문상품에 리뷰를 동시에 N번 작성 |
| `scenarios/4-review-different-members.js` | 서로 다른 회원 N명이 같은 상품에 리뷰를 동시에 작성 |

## ID 범위

실데이터와 섞이지 않게 큰 고정 범위를 쓴다.

| 대상 | 범위 | 쓰는 곳 |
|---|---|---|
| 상품 | 9000001~9000004 | 시나리오 1~4가 하나씩 |
| 회원 | 9100001~ (시나리오 1), 9200001 (2), 9300001 (3), 9400001~9400200 (4) | 회원 헤더(토큰)의 memberId. DB에 회원 행은 없다 |
| 주문상품 | 9300001 (3), 9400001~9400200 (4, 회원 ID와 같은 번호) | 리뷰 작성 자격 |

`cleanup.sql`은 찜 · 찜 수 · 리뷰 · 리뷰 집계 · 상품 복사본을 상품 범위(9000001~9000004)로, 작성 자격을 시드한 주문상품으로만 지운다. 측정이 만드는 행은 늘 측정 상품 안이고, 회원 범위로 넓히면 같은 회원이 다른 상품에 남긴 행까지 지워져 그 상품의 집계와 어긋날 수 있다.

넣기 전에 범위가 비어 있는지 본다(모두 0이어야 한다. 0이 아니면 넣지 말고 범위를 다시 정한다):

```bash
docker exec -i kidly-postgres psql -U kidly -d kidly -c "select (select count(*) from community.replica_product where product_id between 9000001 and 9000004) products, (select count(*) from community.review_eligibility where order_item_id = 9300001 or order_item_id between 9400001 and 9400200) eligibilities, (select count(*) from community.review where product_id between 9000001 and 9000004) reviews, (select count(*) from community.review_stats where product_id between 9000001 and 9000004) review_stats, (select count(*) from community.favorite where product_id between 9000001 and 9000004) favorites, (select count(*) from community.favorite_stats where product_id between 9000001 and 9000004) favorite_stats"
```

## 인증 방식

스크립트는 환경 변수 `AUTH_MODE` 하나로 두 방식 중 하나를 고른다.

| `AUTH_MODE` | 보내는 곳(`BASE_URL` 기본값) | 회원 인증 | 띄울 서비스 |
|---|---|---|---|
| `header`(기본) | community-service `http://localhost:8092` | Gateway가 붙이는 회원 헤더를 직접 붙인다: `X-Member-Id` · `X-Member-Roles: BUYER` · `X-Member-Nickname`(UTF-8 퍼센트 인코딩) | community-service |
| `token` | Gateway `http://localhost:8083` | 개발용 토큰 API `POST /api/v1/dev/tokens`로 받은 `Authorization: Bearer` 토큰 | Gateway · community-service · 회원 서비스 |

- 지금은 `header`로 측정한다. 로컬 회원 서비스가 `member` 스키마 Flyway 충돌(`member_outbox`가 이미 있는데 이력에는 baseline만 있음)로 뜨지 않아 개발용 토큰 API를 쓸 수 없다. 회원 스키마는 커뮤니티가 손대지 않는다
- `header`는 Gateway의 토큰 검증 · 라우팅을 거치지 않으므로 커뮤니티 서비스 자체의 동시성만 잰다. 서비스에 직접 붙어 회원 헤더를 넣는 것은 로컬에서만 한다(운영 · dev에서는 서비스가 Gateway 뒤에만 열린다)
- 회원 서비스가 뜨면 `token`으로 Gateway를 거쳐 돌린다: `AUTH_MODE=token`, `BASE_URL`을 Gateway(8083)로. 토큰 API는 회원 서비스(`DevTokenController`, local · test · dev 프로필에서만 등록)에 있고 Gateway가 `/api/v1/dev/**`를 회원 서비스로 넘긴다
- 회원 행은 만들지 않는다. 커뮤니티는 헤더(토큰)의 회원 ID만 쓰고, 개발용 토큰 API도 회원이 있는지 보지 않는다

## 준비

1. `docker compose up -d` (PostgreSQL · Kafka)
2. 커뮤니티 서비스: `./gradlew :apps:community-service:bootRun` (8092)
3. `token` 방식이면 더: Gateway `./gradlew :apps:gateway:bootRun` (8083), 회원 서비스 `cd apps/member-service && ./gradlew bootRun` (8081, 기본 프로필 `local`)
4. 시드를 넣는다(상품 복사본은 시드로 직접 넣으므로 상품 서비스는 띄우지 않는다)

```bash
docker exec -i kidly-postgres psql -U kidly -d kidly -v ON_ERROR_STOP=1 < domains/community/load-test/seed.sql
```

## 실행

k6는 Mac에 설치하지 않고 Docker 이미지 `grafana/k6:1.0.0`으로 실행한다. 저장소 루트에서 시나리오 파일 이름만 바꿔 실행한다.

`header` 방식(지금 쓰는 방식):

```bash
docker run --rm -v "$PWD/domains/community/load-test:/load-test:ro" -e BASE_URL=http://host.docker.internal:8092 -e N=50 grafana/k6:1.0.0 run /load-test/scenarios/1-favorite-different-members.js
```

`token` 방식(Gateway 경유, 회원 서비스가 뜰 때):

```bash
docker run --rm -v "$PWD/domains/community/load-test:/load-test:ro" -e AUTH_MODE=token -e BASE_URL=http://host.docker.internal:8083 -e N=50 grafana/k6:1.0.0 run /load-test/scenarios/1-favorite-different-members.js
```

- 버전: `1.0.0`으로 고정한다. k6 1.x의 첫 정식 버전이라 1.x 안에서 스크립트 API가 바뀌지 않는다(호환 보장). 저장소에 먼저 쓰던 k6 버전은 없다. 올릴 때는 시나리오 4개를 다시 돌려 확인한다
- 스크립트 폴더는 읽기 전용으로 마운트한다(`/load-test`). 시나리오가 `../lib/common.js`를 읽으므로 폴더째 마운트한다
- 컨테이너 안에서 `localhost`는 컨테이너 자신이라 호스트의 서비스에 닿지 않는다. Docker Desktop(Mac)에서는 `host.docker.internal`로 붙는다. 다른 환경이면 `BASE_URL`만 바꾼다
- 종료 코드: 0이면 모든 check · threshold 통과, 99면 check · threshold 실패(최종 숫자 · 응답 확인 포함). 그 밖의 0이 아닌 코드는 준비 · 확인 단계 실패(setup의 시작 상태 · 토큰 발급, teardown의 확인 요청 `fail`)

설정 값(환경 변수):

| 이름 | 기본값 | 뜻 |
|---|---|---|
| `AUTH_MODE` | `header` | `header`(서비스 직접 + 회원 헤더) 또는 `token`(Gateway + 개발용 토큰) |
| `BASE_URL` | `header`면 `http://localhost:8092`, `token`이면 `http://localhost:8083` | 보내는 곳. Docker 실행이면 `localhost` 대신 `host.docker.internal` |
| `N` | `50` | 동시 요청 수(VU 수). 1~200 (시나리오 4의 작성 자격이 200개) |

시나리오마다 상품 · 회원 범위가 달라 `seed.sql`을 한 번 넣고 1~4를 차례로 돌리면 된다. 같은 시나리오를 다시 돌리려면 `cleanup.sql` → `seed.sql`을 먼저 돌린다(시작 상태가 0이 아니면 setup에서 멈춘다).

## 시나리오와 기대값

모든 VU는 setup이 정한 같은 시각(3초 뒤)까지 기다렸다가 한 번씩 요청한다. setup이 회원별 인증 헤더(또는 토큰)를 먼저 만들고, teardown이 API로 최종 숫자를 읽어 check로 남긴다. check가 하나라도 틀리면 k6가 실패로 끝난다(`checks: rate==1`).

| # | 요청 | 기대 응답 | 기대 최종 숫자(확인 API) |
|---|---|---|---|
| 1 | 회원 N명 → `PUT /api/v1/favorites/9000001` | 모두 204 | `favoriteCount = N` (`GET /api/v1/favorites/summary`) |
| 2 | 회원 1명 × N번 → `PUT /api/v1/favorites/9000002` | 모두 204 | `favoriteCount = 1`, 내 찜(`GET /api/v1/favorites?productIds=9000002`) 1건 |
| 3 | 회원 1명 × N번 → `POST /api/v1/reviews` (주문상품 9300001) | 201 한 번, 409 `REVIEW_ALREADY_WRITTEN` N−1번 | `reviewCount = 1` (요약), 목록 1건 |
| 4 | 회원 N명 → `POST /api/v1/reviews` (각자 주문상품, 상품 9000004) | 모두 201 | `reviewCount = N`, 목록 N건 · 주문상품 겹침 없음, 목록 별점 합 = 기대 합, `averageRating` = 합 ÷ N(소수 첫째 자리 HALF_UP) |

- 시나리오 4의 별점: 회원 ID를 5로 나눈 나머지 + 1. N=50이면 합 150, 평균 3.0
- 시나리오 3의 409는 기대한 결과라 오류율(`http_req_failed`)에서 빼고 `review_created`(1) · `review_conflict`(N−1) 카운터로 따로 센다
- 측정 대상 요청은 `op:write` 태그로 따로 본다. 토큰 발급(`op:setup`, `token` 방식)과 확인 요청(`op:verify`)은 오류율 · p95에 넣지 않는다

## 결과

k6 요약의 `http_reqs{op:write}`(요청 수) · `http_req_failed{op:write}`(오류율) · `http_req_duration{op:write}` p(95)와 teardown의 `[결과]` 줄을 옮긴다.

측정 환경: 2026-11-06, Mac 로컬(Docker Desktop의 PostgreSQL 18 · community-service 한 대), N=50, `grafana/k6:1.0.0`, `AUTH_MODE=header`(community-service 8092 직접)

| 시나리오 | 요청 수 | 오류율(기대한 409 제외) | p95 | 기대 최종 숫자 | 실제 | 일치 |
|---|---|---|---|---|---|---|
| 1. 서로 다른 회원 찜 | 50 | 0% | 253.51ms | favoriteCount = 50 | 50 | ✓ |
| 2. 같은 회원 찜 N번 | 50 | 0% | 156.28ms | favoriteCount = 1, 내 찜 1건 | 1, 1건 | ✓ |
| 3. 같은 주문상품 리뷰 | 50 | 0% | 64.78ms | 201 × 1 · 409 × 49, reviewCount = 1 | 201 × 1 · 409 × 49, 1, 목록 1건 | ✓ |
| 4. 서로 다른 회원 리뷰 | 50 | 0% | 149.33ms | reviewCount = 50, 별점 합 150, 평균 3.0 | 50, 150, 3.0 | ✓ |

- 요청 수 · 오류율 · p95는 측정 대상 요청(`op:write`)만이다. 시나리오 3의 409는 기대한 결과라 오류율에서 뺐다
- 시나리오 1은 서비스를 띄운 뒤 첫 측정이라 p95에 첫 요청 준비 시간이 섞여 있다
- 시나리오 3은 처음 실행에서 409 본문 확인이 오류 코드 이름을 찾아 틀렸다(응답 ProblemDetail에는 코드 이름이 없고 title만 있다). title로 보게 고친 뒤 `cleanup.sql` → `seed.sql` 하고 다시 돌린 결과다. 처음 실행에서도 201 × 1 · 409 × 49 · reviewCount 1은 같았다

## 지우기

측정이 끝나면 원래대로 돌린다:

```bash
docker exec -i kidly-postgres psql -U kidly -d kidly -v ON_ERROR_STOP=1 < domains/community/load-test/cleanup.sql
```
