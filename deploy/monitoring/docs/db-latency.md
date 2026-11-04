# 응답 지연·DB 연결 관측

**Kidly 응답·DB 연결**에서 환경·서비스를 고릅니다. 현재 있는 Hikari와 HTTP timer만 사용합니다.

| 바로 보는 값 | 의미 |
| --- | --- |
| 앱 DB 풀 사용률 | 서비스 풀 상한 대비 활성 연결. DB 서버 전체 상한은 아님 |
| 연결 대기 | 풀에서 연결을 기다리는 요청 수 |
| 연결 시간 초과 | 최근 5분 연결 획득 실패 추정 건수 |
| 평균 획득 시간 | 최근 5분 연결 획득 평균. p95/p99는 아님 |

HTTP histogram은 현재 조회에서 확인되지 않았습니다. 없는 bucket으로 p95/p99를 만들지 않습니다.
필요하면 대표 API·예상 트래픽·bucket 경계를 먼저 정하고 application 설정 변경 승인을 받습니다.
정규화 경로별 평균은 접힌 상세에 두며, 평균만으로 느린 사용자 요청이 없다고 판단하지 않습니다.

## DB 전체 연결 수

dev·prod는 각자 PostgreSQL 컨테이너를 씁니다. 환경별 앱 풀 상한 합계를 해당 환경 DB의 max_connections와 비교해야 합니다.
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

## 남은 완료 기준

- 서비스 풀 크기와 환경별 DB 연결 예산을 담당자와 확정
- histogram 필요 여부·대표 API 목표·부하 시 지연·대기·자원 여유 측정
- 확정한 기준으로 경보를 추가하고 안전한 임계값 시험으로 발화·복구 검증
- DB 전체 연결·락·장기 트랜잭션 지표의 최소 권한 수집 연결

이 항목이 남아 있으므로 화면 추가만으로 #776 전체가 완료된 것은 아닙니다.

## dev 읽기 실측 (2026-11-04)

- Hikari 상한: kidly·추천·정산·회원·결제·상품 각 10개, 관측 합계 60개. 검색·Gateway에는 Hikari 지표가 없었습니다.
- PostgreSQL max_connections=100, superuser_reserved_connections=3. 읽기 조회 세션을 포함한 client backend=61, 락 대기=0, idle in transaction=0.
- 이 시점에 97개의 비관리자 연결 예산이 있다는 뜻이며, 앱 외 관리·마이그레이션·향후 서비스 증가의 여유를 남겨야 합니다. 최대 동시 부하의 안전성 검증은 아닙니다.
- HTTP bucket 시계열은 조회되지 않았습니다. 평균 획득 시간·풀 지표의 실제 화면과 환경·서비스 필터를 확인했습니다. prod DB나 예상 부하는 시험하지 않았습니다.
