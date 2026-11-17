# 부하 테스트 모니터링

k6로 부하를 주는 동안 Grafana **Kidly 부하 테스트** 화면에서 k6 지표(부하를 준 쪽)와 서버 지표(버틴 쪽)를 같은 시간축으로 봅니다.
k6가 결과를 모니터링 Prometheus에 remote write로 보내고, 대시보드가 `testid` 태그로 테스트 한 번을 골라 보여 줍니다.

- 대상 환경은 운영(dev) 하나입니다. 서버 지표는 `env="dev"` 기준입니다
- 서버 p95는 없습니다. 앱에 히스토그램 설정이 없어서 응답 분위수(p95 · p99)는 k6가 잰 값으로만 봅니다
- 앱 · 서버 설정은 바꾸지 않습니다. k6 실행 옵션만 붙입니다

## 실행 위치

| 위치 | 쓰는 경우 | 비고 |
|---|---|---|
| **개발자 PC + SSH 터널 (기본)** | 평소 · 발표 준비 | 30090 포트를 밖에 열지 않습니다. 터널로만 보냅니다 |
| 모니터링 EC2(t3.medium)에서 실행 (대안) | PC 회선이 느려 k6 자체가 병목일 때 | 모니터링 스택(Prometheus · Loki · Grafana)과 CPU를 나눠 씁니다. 짧은 테스트만 돌립니다 |
| 앱 EC2(t3.large)에서 실행 | 쓰지 않습니다 | 부하 발생기가 앱과 CPU · 메모리를 나눠 써서 결과가 왜곡됩니다 |

## 1. SSH 터널 열기 (개발자 PC)

모니터링 EC2의 Prometheus는 NodePort 30090입니다. 보안 그룹은 앱 서버에서만 이 포트를 엽니다([설치](setup.md)). PC에서는 SSH(22)로 들어가 그 서버의 30090을 내 PC의 9090으로 끌어옵니다.

```bash
# 터미널 하나를 열어 두고 테스트가 끝날 때까지 유지한다
ssh -i <team04 키 경로> -N -L 9090:127.0.0.1:30090 ec2-user@<모니터링 EC2 탄력적 IP>
```

- `-N`은 명령을 실행하지 않고 터널만 엽니다. 끝낼 때는 `Ctrl+C`로 닫습니다
- 확인: 다른 터미널에서 `curl -s localhost:9090/-/ready` → `Prometheus Server is Ready.`
- 키 · 접속 정보는 팀이 쓰는 SSH 키와 같습니다. 저장소 · 채팅에 키 값을 남기지 않습니다

## 2. testid 짓기

`testid`는 대시보드에서 테스트 한 번을 고르는 이름입니다. 겹치지 않게 아래 형식을 씁니다.

```text
<날짜>-<도메인>-<시나리오>[-<회차>]
예) 20261120-community-favorite-diff-1
    20261120-notification-unread-hot-vu200
```

- 영문 소문자 · 숫자 · `-`만 씁니다
- 같은 시나리오를 다시 돌리면 회차를 바꿉니다. 같은 testid로 두 번 보내면 두 실행이 한 줄로 섞입니다

## 3. 실행

```bash
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max'

k6 run -o experimental-prometheus-rw --tag testid=<이름> <시나리오.js>
```

- `K6_PROMETHEUS_RW_TREND_STATS`가 없으면 p95 · p99가 전송되지 않습니다. 대시보드의 응답 시간 패널이 비면 이 값을 먼저 확인합니다
- k6는 5초마다 전송합니다. 테스트가 끝나도 마지막 값이 약 5분 동안 그래프에 남습니다
- 요청에 `tags: { name: '<API 이름>' }`을 붙이면 대시보드의 "API별 p95"가 그 이름으로 나뉩니다. 없으면 URL마다 따로 나옵니다

### 기존 스크립트에 붙이는 예

스크립트 파일은 고치지 않습니다. 실행 명령에 옵션만 더합니다.

커뮤니티(`domains/community/load-test`, 실행 방법은 그 폴더의 README):

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max' \
BASE_URL=<대상 주소> AUTH_MODE=<README 참고> N=<동시 요청 수> \
k6 run -o experimental-prometheus-rw --tag testid=20261120-community-favorite-diff-1 \
  domains/community/load-test/scenarios/1-favorite-different-members.js
```

알림(`domains/notification/load-test`): `run.sh`는 로컬 서비스용으로 Docker 안에서 k6를 돌리고 결과 전송 옵션이 없습니다. 운영 서버 대상으로는 `run.sh` 대신 시나리오를 직접 실행합니다(환경 변수 `BASE_URL` · `VUS` · `DURATION` · `AUTH_MODE`는 그 폴더의 README를 따릅니다).

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max' \
BASE_URL=<운영 Gateway 주소> VUS=100 DURATION=60s AUTH_MODE=<README 참고> \
k6 run -o experimental-prometheus-rw --tag testid=20261120-notification-unread-hot-vu100 \
  domains/notification/load-test/scenarios/2-unread-count-hot.js
```

Docker로 k6를 돌릴 때는 컨테이너 안의 `localhost`가 PC가 아니므로 주소를 `http://host.docker.internal:9090/api/v1/write`로 바꿉니다.

### 모니터링 EC2에서 실행할 때 (대안)

터널 없이 같은 서버의 NodePort로 보냅니다. k6는 그 서버에 따로 설치합니다.

```bash
K6_PROMETHEUS_RW_SERVER_URL=http://127.0.0.1:30090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),avg,max' \
k6 run -o experimental-prometheus-rw --tag testid=<이름> <시나리오.js>
```

이 서버는 t3.medium이라 모니터링 스택과 자원을 나눠 씁니다. VU를 크게 올리는 테스트는 PC에서 돌립니다.

## 4. Grafana에서 보기

Grafana → Kidly → **Kidly 부하 테스트** (운영 홈 · 서비스 상태 화면 상단의 "부하 테스트" 링크)

1. **테스트**: 방금 쓴 testid를 고릅니다(최근 것이 위에 있습니다)
2. **서비스**: 부하가 지나가는 서비스만 고릅니다. 예: 알림 조회 → `kidly-gateway`, `kidly-notification`
3. **시간 범위**: 테스트 시작 1분 전 ~ 끝난 1분 뒤로 맞춥니다
   - 그래프에서 구간을 드래그하면 그 구간으로 바뀝니다
   - 상단 요약(보낸 요청 수 · 최대 VU · 실패율)은 선택한 시간 범위로 계산합니다. 범위가 테스트 끝을 포함해야 전체 숫자가 나옵니다
4. 위쪽 "k6"는 부하를 준 쪽, 아래쪽 "서버"는 버틴 쪽입니다. 같은 시각에서 위아래를 비교합니다
   - k6 초당 요청이 VU를 늘려도 더 오르지 않는 지점: 서버 처리량의 한계
   - 그 시각의 서버 평균 응답 · CPU · DB 연결 기다림: 무엇이 먼저 막혔는지
   - 보라색 세로선은 서비스 재시작입니다. 부하 중에 나오면 OOM · 헬스체크 실패를 확인합니다
5. 맨 아래 "서버 한계"는 결과를 앱 한계로 말하기 전에 확인합니다
   - **서버 CPU · steal / 메모리 여유 (앱 · DB)**: DB 서버에는 Postgres · Kafka · Elasticsearch · 모니터링이 함께 있습니다. 앱 서버보다 DB 서버가 먼저 100%(메모리는 10% 아래)에 닿으면 병목은 DB 쪽입니다. 사용률이 100%에 붙으면 노드가 먼저 한계입니다. steal이 오르면 t3 CPU 크레딧이 떨어졌을 수 있으니 AWS 콘솔 → EC2 → 모니터링 → `CPUCreditBalance`를 같은 시각으로 캡처해 둡니다(Prometheus에는 없음)
   - **서비스별 CPU(코어)**: 앱 서버는 2코어이고 컨테이너 CPU 한도가 없어 스로틀링 지표는 없습니다. 합이 2에 가까우면 노드 CPU가 상한입니다
   - **서비스별 파드 수**: 추천 서비스 자동 확장(1~2개)이 늘었는지 봅니다
   - **Gateway 속도 제한(429)**: 값이 있으면 k6 실패 중 429는 서버 한계가 아니라 제한입니다. 요약의 실패율은 429를 빼고, 429 비율은 따로 보여 줍니다
   - 응답 시간은 k6를 돌린 PC 기준(네트워크 포함, 사용자 체감)입니다. 발표 자료에 그렇게 적습니다

## 지표 이름 (k6 v1.x, experimental-prometheus-rw)

| 대시보드 | 지표 |
|---|---|
| 요청 수 · 초당 요청 | `k6_http_reqs_total` (라벨 `testid` · `name` · `status` · `expected_response`) |
| 응답 시간 | `k6_http_req_duration_p95` · `_p99` · `_avg` (초 단위, 테스트 시작부터 누적) |
| 실패율 | `k6_http_reqs_total{expected_response="false",status!="429"}` ÷ 전체 (429는 `status="429"` 따로) |
| 앱 서버 · 파드 · 속도 제한 | `node_cpu_seconds_total`(idle · steal) · `container_cpu_usage_seconds_total`(namespace `kidly-dev`) · `kidly_gateway_rate_limited_total`(라벨 `target`) |
| 가상 사용자 | `k6_vus` · `k6_vus_max` |
| check | `k6_checks_rate` (라벨 `check`) |

`k6_http_req_failed_rate`는 상태코드 라벨별로 나뉘어 0 아니면 1이라 전체 실패율로 쓰지 않습니다.

## 끝나면

- 터널을 닫습니다(`Ctrl+C`)
- k6 지표는 Prometheus 보관 기간(15일) 동안 남습니다. 발표 자료에는 testid와 시간 범위를 함께 적어 두면 다시 열 수 있습니다
