# 응답 지연·DB 연결 관측

**Kidly 응답·DB 연결**에서 환경·서비스를 고릅니다. 현재 있는 Hikari와 HTTP timer만 사용합니다.

| 바로 보는 값 | 의미 |
| --- | --- |
| 앱 DB 풀 사용률 | 서비스 풀 상한 대비 활성 연결. DB 서버 전체 상한은 아님 |
| 연결 대기 | 풀에서 연결을 기다리는 요청 수 |
| 연결 시간 초과 | 최근 5분 연결 획득 실패 추정 건수 |
| 평균 획득 시간 | 최근 5분 연결 획득 평균. p95/p99는 아님 |

## API 지연과 DB 풀을 함께 확인하기

1. **Kidly 로그 → API 처리 시간**에서 운영·서비스를 고릅니다. 선택한 종료 시각 직전 30분 동안 20건 이상 기록된 경로의 앱 처리 p95가 최대 10개 표시됩니다. 표본이 적은 경로는 목록에서 빠집니다.
2. 같은 시간대의 **Kidly 응답·DB 연결**에서 연결 대기·시간 초과·풀 사용률을 봅니다. 여유 연결이 0이고 요청 대기가 같은 풀에서 5분 지속되면 DB 연결 대기 경보가 발생합니다.
3. 배포·배치와 겹치면 **서버**에서 CPU·가용 메모리를 비교합니다. 사용자 체감 지연은 Gateway를 통과한 클라이언트 응답 시간으로 따로 잽니다.

[기준선과 운영 확인 기록](https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE/issues/995)은 #995에 모읍니다. 대표 부하 재측정에는 대상 API·요청률·동시성·표본 수와 클라이언트 p95·오류율·DB 대기를 같은 시간대로 기록합니다. 측정 결과로 API 지연 경보의 기준과 지속 시간을 합의합니다.

## DB 전체 연결 수

현재 운영은 단일 k3s 환경입니다. 운영 앱의 풀 상한 합계를 해당 PostgreSQL의 max_connections와 비교합니다.
팀 댓글의 서비스별 기본 풀 10개, 전체 분리 시 110~120개 예상은 계획값입니다. 현재 실제 풀 상한·DB 수치는 별도로 측정하고 풀 크기를 정한 후 경보 기준을 확정합니다.

DB 담당자가 기존 인증 경로로 다음 읽기 전용 SQL을 확인할 수 있습니다. 결과에 원시 SQL·사용자 ID·접속자 주소를 넣지 않습니다.

```sql
SELECT current_setting('max_connections')::int AS max_connections,
       current_setting('superuser_reserved_connections')::int AS reserved_superuser;
SELECT count(*) AS client_connections,
       count(*) FILTER (WHERE wait_event_type = 'Lock') AS lock_waiting,
       count(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction,
       max(EXTRACT(EPOCH FROM (now() - xact_start))) AS oldest_transaction_seconds
FROM pg_stat_activity
WHERE backend_type = 'client backend';
```

상한에는 예약 연결·관리·마이그레이션·비앱 클라이언트 여유를 남깁니다. 앱 풀 합계와 관측된 실제 연결 수는 다릅니다.
DB 통계를 정기 수집하려면 기존 Alloy의 PostgreSQL 수집 기능과 최소 조회 권한을 검토합니다. 새 DB 계정·권한·비밀값 연결은 확인 후 진행하며, 현재 PR은 exporter·계정·풀 설정을 추가하지 않습니다.

## 이전 dev 읽기 실측 (2026-11-04, 서비스 11개 기준)

- Hikari 상한: kidly·추천·정산·회원·결제·상품·주문·커뮤니티·알림 각 10개, 관측 합계 90개. 검색·Gateway에는 Hikari 지표가 없었습니다.
- PostgreSQL max_connections=100, superuser_reserved_connections=3. 읽기 조회 세션을 포함한 client backend=91, 락 대기=0, idle in transaction=0.
- 비관리자 연결 상한 97개 중 앱 풀 상한이 90개입니다. 관리·마이그레이션·비앱 클라이언트에 남길 여유가 작으므로 서비스 추가 전에 연결 예산을 확정해야 합니다. 현재 정상 응답이 최대 동시 부하의 안전성을 뜻하지 않습니다.
- 서비스 구성이 달라지면 운영 연결 예산을 다시 측정합니다.
- HTTP bucket 시계열은 조회되지 않았습니다. 평균 획득 시간·풀 지표의 실제 화면과 환경·서비스 필터를 확인했습니다. prod DB나 예상 부하는 시험하지 않았습니다.

## 상시 수집 제안 (승인 전, 미적용)

1. 기존 Alloy의 내장 PostgreSQL exporter를 사용합니다. 새 이미지나 유료 서비스는 추가하지 않습니다. 기본 collector는 끄고 사용자 쿼리로 전체 client backend 건수·max_connections·예약 연결 수만 수집합니다. SQL 본문·접속자·업무 테이블은 수집하지 않습니다.
2. 환경별 전용 로그인 계정은 연결 1개, 읽기 전용 기본값, 쿼리 제한 2초로 제한합니다. superuser·pg_monitor·업무 테이블 권한은 부여하지 않습니다. dev에서 집계 값이 관리자 읽기 점검과 같은지 먼저 확인합니다. 락·장기 트랜잭션은 제한 권한으로 값이 보이지 않을 수 있어 별도 검토합니다.
3. 비밀번호는 GitHub 환경 Secret으로 주입하고 저장소·PR·지표 라벨에 넣지 않습니다. DB 공개 포트는 추가하지 않습니다.
4. 화면에서 DB 전체 연결/비관리자 상한, 앱 풀 상한 합계/비관리자 상한을 함께 비교합니다. 연결 예산 경보는 담당자 합의 전 추가하지 않습니다.

새 DB 계정·권한·비밀값 연결은 승인 후 구현합니다. 현재 PR에는 생성 SQL·비밀번호·활성 수집 설정이 없습니다.

근거: [Alloy PostgreSQL exporter](https://grafana.com/docs/alloy/latest/reference/components/prometheus/prometheus.exporter.postgres/)의 `disable_default_metrics`와 `custom_queries_config_path`. 실제 배포 버전에서도 설정 검증 후 사용합니다.
