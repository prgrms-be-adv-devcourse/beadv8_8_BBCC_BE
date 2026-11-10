# 알림함 안 읽은 개수 부하 측정 (#1107)

헤더 배지가 페이지마다 부르는 안 읽은 개수 API(`GET /api/v1/notifications/unread-count`)가 동시 사용자를 얼마나 버티는지 k6로 잰다. 알림 도메인에서 가장 자주 불리는 API다.

- 로컬 전용이다. CI에서 돌리지 않는다
- 이 폴더는 Gradle 소스 세트 밖이라 빌드 · 테스트에 들어가지 않는다

## 결과 요약

- 문제: 알림이 몰린 회원(안 읽음 18만 건)은 VU를 10 → 200으로 올려도 TPS가 약 400에서 멈추고 p95가 31ms → 584ms로 늘었다. 커넥션 풀(5개)이 계속 꽉 차 있었다(사용 중 평균 4.5 이상, 대기 ≈ VU 수)
- 원인: 메서드 이름으로 만든 count 쿼리(`countBy…ReadAtIsNull`)가 `count(id)`로 나간다. 안 읽은 알림 부분 인덱스에는 `id`가 없어서 세는 행마다 테이블을 다시 읽는다(Bitmap Heap Scan, 버퍼 4,538)
- 해결: `count(*)` JPQL로 바꿨다. 같은 인덱스만 읽는다(Index Only Scan, Heap Fetches 0, 버퍼 145). 스키마는 그대로다
- 결과: 몰린 회원 TPS 약 400 → 약 800~860(VU 50 제외, 아래 표), p95는 모든 VU 단계에서 절반 이하(VU 200에서 584ms → 248ms). 보통 회원(안 읽음 8건)은 수정 전에도 VU 200에서 p95 67ms였고, 병목은 DB가 아니었다

## 파일

| 파일 | 내용 |
|---|---|
| `seed.sql` | 준비 데이터 100만 건. 다시 돌려도 안전하다. 끝에 `VACUUM ANALYZE` |
| `cleanup.sql` | 아래 받는 사람 범위의 알림만 지운다. 다시 돌려도 안전하다 |
| `lib/common.js` | 시나리오 공통: 인증 방식 · VU 단계 설정 · 안 읽은 개수 요청과 확인 |
| `scenarios/1-unread-count-spread.js` | 회원 1만 명 중 무작위 회원이 쉬지 않고 묻는다. 응답 `count = 8`을 확인한다 |
| `scenarios/2-unread-count-hot.js` | 알림이 몰린 회원 1명이 쉬지 않고 묻는다. 응답 `count = 180000`을 확인한다 |
| `run.sh` | 시나리오 하나를 VU 단계별(기본 10 50 100 200)로 돌리고, 도는 동안 actuator에서 Hikari 커넥션 풀을 0.5초마다 읽어 단계마다 한 줄로 요약한다 |

## ID 범위

실데이터와 섞이지 않게 큰 고정 범위의 회원(`recipient_type = 'MEMBER'`)만 쓴다. 회원 행은 만들지 않는다(알림함은 받는 사람 ID만 쓴다).

| 대상 | 받는 사람 ID | 건수 | 안 읽음 |
|---|---|---|---|
| 분산 회원 1만 명 | 9500001~9510000 | 회원마다 80건, 모두 80만 건 | 회원마다 최근 8건 |
| 몰린 회원 1명(주문이 많은 판매자, 하루 2,880건 × 70일) | 9600001 | 20만 건 | 최근 18만 건 |

- `dedup_key`는 `loadtest:`로 시작한다. 생성 시각은 보존 기간(90일) 안에 둔다
- 넣기 전에 범위가 비어 있는지 본다(0이어야 한다):

```bash
docker exec -i kidly-postgres psql -U kidly -d kidly -c "select count(*) from notification.inbox where recipient_type = 'MEMBER' and (recipient_id between 9500001 and 9510000 or recipient_id = 9600001)"
```

## 인증 방식

커뮤니티 load-test와 같다. `AUTH_MODE`로 고른다.

| `AUTH_MODE` | 보내는 곳(`BASE_URL` 기본값) | 회원 인증 |
|---|---|---|
| `header`(기본) | notification-service `http://localhost:8090` | Gateway가 붙이는 회원 헤더를 직접 붙인다: `X-Member-Id` · `X-Member-Roles: BUYER` · `X-Member-Nickname` |
| `token` | Gateway `http://localhost:8083` | 개발용 토큰 API `POST /api/v1/dev/tokens`의 Bearer 토큰. 회원마다 발급하므로 시나리오 1은 `MEMBERS`(기본 10000)로 회원 수를 줄인다 |

측정은 `header`로 했다. Gateway의 토큰 검증 · 라우팅을 빼고 알림 서비스와 DB만 잰다.

## 준비

1. `docker compose up -d` (PostgreSQL · Kafka)
2. 알림 서비스. 커넥션 풀을 dev · prod와 같은 5개로 맞춘다(`application.yml`의 dev · prod `hikari.maximum-pool-size: 5`, 로컬 기본은 10):

```bash
SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE=5 ./gradlew :apps:notification-service:bootRun
```

3. 시드(약 20초):

```bash
docker exec -i kidly-postgres psql -U kidly -d kidly -v ON_ERROR_STOP=1 < domains/notification/load-test/seed.sql
```

## 실행

k6는 Mac에 설치하지 않고 Docker 이미지 `grafana/k6:1.0.0`(커뮤니티 load-test와 같은 버전)으로 실행한다. `run.sh`가 컨테이너를 띄운다. 저장소 루트에서:

```bash
domains/notification/load-test/run.sh 1-unread-count-spread.js
```

```bash
domains/notification/load-test/run.sh 2-unread-count-hot.js "10 50 100 200"
```

| 환경 변수 | 기본값 | 뜻 |
|---|---|---|
| `SERVICE_URL` | `http://localhost:8090` | 호스트에서 본 서비스 주소(actuator를 읽는다). k6 컨테이너에는 `localhost`를 `host.docker.internal`로 바꿔 넘긴다 |
| `DURATION` | `30s` | VU 단계 하나의 측정 시간 |
| `AUTH_MODE` | `header` | 위 "인증 방식" |

- 각 VU는 쉬지 않고 요청한다(think time 없음). 그래서 TPS는 그 VU 수에서 낼 수 있는 최대 처리량이다
- 출력: k6 요약의 `{op:count}` 줄(p95 · 오류율 · TPS)과 `[VU n] Hikari …` 한 줄(풀 최대 · 사용 중 평균/최대 · 대기 평균/최대 · 커넥션 얻기 평균)
- check 하나라도 틀리거나 오류가 있으면 k6가 99로 끝나지만, `run.sh`는 다음 단계로 넘어간다

## 측정 결과

측정 환경: 2026-11-10, Mac(10코어 · 24GB) 한 대에 PostgreSQL 18(Docker) · notification-service · k6(Docker)를 함께 띄웠다. 커넥션 풀 5, `AUTH_MODE=header`, 단계마다 20초.

수정 전/후 jar를 번갈아 띄워(전 → 후, 후 → 전, 전 → 후) 3회씩 쟀고, 표는 3회의 중앙값이다. 띄울 때마다 10초 워밍업을 했다. 같은 Mac에서 다른 프로그램이 CPU를 쓰고 있어 한 번만 재면 값이 크게 흔들려서 이렇게 쟀다.

### 몰린 회원(시나리오 2, 안 읽음 18만 건)

| VU | TPS 전 → 후 | p95 전 → 후 | 오류율 전 / 후 | 커넥션 풀 사용 중 평균(최대 5) 전 / 후 | 커넥션 대기 평균 전 / 후 | 커넥션 얻기 평균 전 → 후 |
|---|---|---|---|---|---|---|
| 10 | 402 → 856 | 31.4ms → 14.3ms | 0% / 0% | 4.7 / 4.7 | 4.5 / 4.4 | 11.8ms → 5.3ms |
| 50 | 417 → 468 | 141.4ms → 63.4ms | 0% / 0.05% | 5.0 / 2.4 | 42.5 / 18.3 | 106.8ms → 45.0ms |
| 100 | 379 → 794 | 351.7ms → 138.2ms | 0% / 0.03% | 4.3 / 4.8 | 87.4 / 89.9 | 248.2ms → 108.5ms |
| 200 | 409 → 806 | 583.7ms → 248.3ms | 0% / 0.11% | 4.5 / 4.3 | 174.6 / 168.1 | 467.1ms → 217.8ms |

- 수정 전: VU를 20배 올려도 TPS는 380~420으로 같고, 늘어난 VU는 커넥션 대기열에 쌓였다(대기 ≈ VU − 5). 요청 하나가 커넥션을 쥐는 시간(약 12ms) × 풀 5개가 상한이다
- 수정 후: 같은 풀 5개로 TPS가 약 2배다. 쿼리가 짧아져 커넥션을 쥐는 시간이 줄었다
- 수정 후 VU 50의 TPS(3회 431 · 468 · 534)가 다른 단계보다 낮은 것은 측정 도구 쪽 연결 실패 때문이다. p95(63ms)는 다른 단계와 같은 흐름이다. 아래 "오류율"
- 수정 후에도 풀은 꽉 차 있다. 한계는 18만 건을 세는 시간(인덱스만 읽어도 약 10ms)이다. 아래 "남은 한계"

### 보통 회원(시나리오 1, 안 읽음 8건) — 수정 전

| VU | TPS | p95 | 오류율 | 커넥션 풀 사용 중 평균 | 커넥션 대기 평균 | 커넥션 얻기 평균 |
|---|---|---|---|---|---|---|
| 10 | 2,133 | 2.2ms | 0% | 4.5 | 1.5 | 0.3ms |
| 50 | 1,214 | 10.2ms | 0.24% | 1.2 | 5.6 | 3.1ms |
| 100 | 2,139 | 20.9ms | 0.11% | 1.4 | 16.2 | 8.3ms |
| 200 | 2,014 | 67.2ms | 0.08% | 1.8 | 53.2 | 30.4ms |

- p95 67ms 이하로 VU 200(약 2,000 TPS)까지 버틴다. 쿼리는 0.07ms(아래 EXPLAIN)이고 커넥션 풀 사용 중 평균이 2개 아래라 DB · 풀은 한계가 아니다. 한계는 Mac 한 대에서 k6 · 서비스 · DB가 CPU를 나눠 쓰는 것이다(측정 중 CPU 유휴 1~10%)
- TPS는 회차마다 600~6,400으로 흔들렸다(같은 Mac의 다른 프로그램). 수정 효과(0.065ms → 0.040ms)는 이 잡음보다 작아 전후를 표로 비교하지 않는다

### 오류율

- 오류는 모두 k6 컨테이너가 호스트의 서비스에 연결하지 못한 `dial: i/o timeout`이다(Docker Desktop 포트 프록시). 서비스가 받은 요청은 모두 200이었다(actuator `http_server_requests_seconds_count`에 5xx 없음)
- 연결 실패 한 번에 VU 하나가 몇 초씩 멈춰서 그 단계의 TPS가 낮게 나올 수 있다

## 실행 계획 (EXPLAIN (ANALYZE, BUFFERS))

앱이 보내는 SQL(Hibernate SQL 로그에서 확인):

- 수정 전: `select count(in1_0.id) from notification.inbox in1_0 where in1_0.recipient_type=? and in1_0.recipient_id=? and in1_0.read_at is null`
- 수정 후: `select count(*) from notification.inbox in1_0 where in1_0.recipient_type=? and in1_0.recipient_id=? and in1_0.read_at is null`

인덱스는 수정 전후 같다: `idx_inbox_recipient_unread ON notification.inbox (recipient_type, recipient_id) WHERE read_at IS NULL`. 실행 시간은 20회 중앙값이다.

| 받는 사람 | 수정 전 `count(id)` | 수정 후 `count(*)` |
|---|---|---|
| 몰린 회원(18만 건) | Bitmap Index Scan → Bitmap Heap Scan(Heap Blocks 4,391), 버퍼 4,538, 17.9ms | Index Only Scan, Heap Fetches 0, 버퍼 145, 10.4ms |
| 보통 회원(8건) | Index Scan, 버퍼 14, 0.065ms | Index Only Scan, Heap Fetches 0, 버퍼 4, 0.040ms |

- `count(id)`는 `id`가 NULL인지 봐야 해서 인덱스에 없는 `id`를 읽으러 테이블에 간다. `count(*)`는 행이 있는지만 세서 인덱스로 끝난다
- Index Only Scan은 가시성 맵(visibility map)이 채워져 있어야 테이블을 읽지 않는다. 시드는 끝에 `VACUUM ANALYZE`를 돌린다. 운영에서는 autovacuum이 채운다

## 남은 한계

- 몰린 회원은 수정 후에도 요청마다 인덱스 항목 18만 개를 센다(약 10ms). 커넥션 풀 5개에서 약 800 TPS가 상한이고, 그 위의 요청은 커넥션 대기열에 쌓인다
- 보통 회원(안 읽음 수십 건)은 0.1ms 아래라 문제없다. 18만 건은 하루 2,880건 주문을 받는 판매자가 70일간 알림함을 열지 않은 극단적인 경우다
- 더 줄이려면 세는 상한을 두거나(배지는 "99+"로 보이므로 100건까지만 센다) 안 읽은 개수를 따로 저장해야 한다. 앞의 것은 API 응답의 의미가 바뀌고 뒤의 것은 쓰기 경로가 바뀌므로 이번에는 하지 않았다

## 지우기

```bash
docker exec -i kidly-postgres psql -U kidly -d kidly -v ON_ERROR_STOP=1 < domains/notification/load-test/cleanup.sql
```
