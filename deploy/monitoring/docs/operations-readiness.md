# 오픈 점검·장애 대응

**진입: Grafana → Kidly 서비스 오류 → 환경·서비스 선택 → 같은 시간 로그 확인**
지표 수집 성공만으로 주문·결제 성공을 보장하지 않습니다. 데이터 없음은 정상 표시가 아닙니다.

## 알림을 받으면

| 알림 | 첫 확인 | 복구 확인 |
| --- | --- | --- |
| 앱 다운·반복 5xx·ERROR | 환경·문제 서비스·배포 이력, 관련 시간 로그 | 해당 서비스 UP·요청 성공·해결 알림 |
| 개별 수집 누락 | 운영 의도와 targets·Alloy 수집 | 기대 서비스 전부 최신 데이터 수신 |
| 수집기 전체·전송/저장 실패 | Alloy 연결·queue·유실·저장소 상태 | 전송 회복·누락 범위와 실제 유실 기록 |
| 서버 메모리·디스크·OOM (#773) | 서버와 컨테이너 사용량·커널 기록 | 자원 여유·재시작 원인·알림 해제 |
| Kafka 정체 (#984) | 알림의 그룹·토픽과 Kafka 대시보드에서 lag·커밋 오프셋 확인 | 오프셋 전진·lag 감소·업무 처리 완료 |
| 외부 경로·감시 서버 (#987) | Actions의 `external-observation` 수동 실행으로 API·Grafana·TLS 확인 | 외부에서 정상 응답 확인 |
| 검색 색인 실패·DLT 미반영·임베딩 차단·벡터 없는 상품 (#1026) | Elasticsearch 상태, `deploy/search/README.md` "장애 대응" | 검색 API 200·DLT lag 0·지표 0 |
| 정산 실행 지연·정산 지급 실패·정산 지급 결과 없음·알림 발송 지연 (#1223) | 아래 "알림 레벨"의 정산·알림 규칙 | 해결 알림·업무 상태 |

당번은 상황과 사용자 영향을 공유하고, 해당 도메인 담당이 업무 상태·재처리를 판단합니다.
담당자 실명·연락 경로·당번 시간은 팀이 지정해야 합니다. 특정 담당자가 정해졌다고 가정하지 않습니다.

## 알림 레벨

로그 레벨(ERROR·WARN)은 진단용입니다. 사람을 부르는 알림은 지표 규칙의 `severity` 라벨로 나눕니다.
두 레벨 모두 같은 Slack 연락처(`slack-ops`)로 가고, 제목 표시(critical 🔴, warning 🟠)만 다릅니다(`templates.yml`).

- **critical**: 바로 사람이 행동해야 하는 증상. 판매자에게 돈이 나가지 않거나 업무가 멈춘 경우
- **warning**: 근무 시간에 확인
- **알림 없음**: 규칙을 두지 않고 지표로만 봅니다(재시도 횟수·처리 시간 등)

근거: Prometheus [Alerting](https://prometheus.io/docs/practices/alerting/)(원인이 아니라 증상에 알리고, 사람을 부르는 알림은 적게) ·
Google SRE book [Monitoring Distributed Systems](https://sre.google/sre-book/monitoring-distributed-systems/)

### 정산·알림 규칙 (#1223)

규칙 파일은 `grafana/provisioning/alerting/settlement.yml`·`notification.yml`입니다. 모두 1분마다 평가하고 `for: 0m`, 데이터가 없으면 OK입니다.

| 규칙 | 조건 | severity | 받으면 |
| --- | --- | --- | --- |
| 정산 실행 지연 | `settlement_run_overdue` > 0: 영업일 마감 시각(`SETTLEMENT_RUN_DEADLINE`, 기본 12:30)까지 그날 정산 실행이 끝나지 않았거나, 그해 공휴일 목록이 없음 | critical | `kubectl -n kidly-dev get cronjob,jobs`로 그날 `settlement-daily-run` Job의 성공·실패를 봅니다. 실패했으면 `POST /api/v1/admin/settlements/run`으로 다시 실행합니다. 수동 실행은 진행이 `stale-run-after`(기본 30분) 넘게 없는 실행을 FAILED로 닫고 정산합니다. 30분을 기다리지 않으려면 Job Pod가 끝난 것을 확인한 뒤 `POST /api/v1/admin/settlement-runs/{runId}/recover`로 먼저 닫습니다(실제로 도는 실행과 구분하지 못합니다). CronJob 재시도 Pod는 앞 Pod가 남긴 자동 실행을 바로 닫고 정산합니다. 공휴일 목록이 없으면 공휴일 갱신 로그를 봅니다 |
| 정산 지급 실패 | `increase(settlement_payout_failed_total[10m])` > 0: 결제가 `payment.payout-failed`를 보냄 | critical | 실패한 정산서는 자동 재요청하지 않고, 관리자 재요청(`retry`)도 받지 않습니다. `GET /api/v1/admin/settlements`의 `failReason`을 보고 판매자 지급을 직접 처리합니다 |
| 정산 지급 결과 없음 | `settlement_payout_oldest_unpaid_age_seconds` > 7200 | critical | 30분 넘게 결과가 없는 정산서는 10분마다 자동 재요청합니다. 그래도 2시간이 지났으니 정산 아웃박스(`settlement_outbox_unsent`)·결제 서비스·Kafka를 확인합니다. 7일이 지나 자동 재요청이 멈춘 정산서는 `POST /api/v1/admin/settlements/{settlementId}/retry` |
| 알림 발송 지연 | `kidly_notification_oldest_unsent_age_seconds` > 1200. 정상 재시도(30초·2분·10분 뒤)도 마지막 시도까지 12분 30초가 걸림 | warning | 알림 서비스 로그의 발송 실패 원인과 `kidly_notification_sends_total`(채널·결과별)을 봅니다. 모두 보내면 해제됩니다 |

- 서비스가 내려가 지표가 오지 않으면 위 규칙은 데이터 없음이라 조용하고, 기존 `서비스 다운`·`수집 끊김`(critical)이 대신 알립니다. 정산·알림의 ERROR 로그 급증은 기존 `ERROR 로그`(warning)가 봅니다
- 알림 없는 지표: `settlement_payout_retry_total`·`settlement_outbox_send_delay_seconds`·`settlement_payout_duration_seconds`·`settlement_dead_letter_total`·`kidly_notification_unsent`. 아직 대시보드 패널이 없어 Grafana Explore에서 조회합니다

## 안전하게 조치하기

- 배포 직후면 실패 단계와 직전 정상 버전을 확인합니다. DB 마이그레이션 호환을 확인하지 않은 SHA 롤백은 하지 않습니다.
- 일시 중지는 Actions `environment-operation`에서 선택합니다. DB·Kafka도 함께 중지되고 데이터는 유지됩니다. 임의 파일 삭제로 알림을 끄지 않습니다.
- 운영은 단일 k3s 환경입니다. Grafana의 **운영**은 수집 라벨 `dev`, Kubernetes 네임스페이스 `kidly-dev`를 가리킵니다.
- 토픽·오프셋·DLT·DB 상태를 임의로 수정하거나 중복 주문·결제·정산을 재실행하지 않습니다. 도메인 담당의 재처리·멱등성 절차를 따릅니다.
- 해결 알림만 보고 종료하지 않고 외부 요청·업무 상태·누락 데이터를 확인합니다. 발생·인지·조치·복구 시각과 남은 위험을 기록합니다.

## 오픈 완료 기준

| 확인 | 담당 | 현재 근거·미완료 |
| --- | --- | --- |
| 운영 앱 수집·의도적 중지 구분 | 배포/모니터링 담당 | #771·#772 및 `environment-operation` |
| 서버 여유·OOM·디스크 경보 | 인프라 담당 | [롤링 배포 실측](https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE/issues/777#issuecomment-6815534337). 전체 환경 기동 피크와 Pod 재시작 확인 필요 |
| 서비스 지연·DB 연결 예산 | 앱/DB 담당 | [API별 p95 및 Hikari 경보](https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE/issues/995). 대표 부하·지연 기준 합의 필요 |
| Kafka 처리·DLT·아웃박스 | 각 도메인 담당 | [Kafka 정체 발화·복구와 Slack 전달 확인](https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE/issues/984) |
| 외부 가용성·TLS | 인프라 담당 | 수동 진단 성공. [운영시간·상시 감시 결정 대기](https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE/issues/987) |
| 주문·결제·정산 업무 정상 | 각 도메인 담당 | 성공/실패·장기 대기·불일치, 정산 마지막 성공/미처리 확인 |
| DB 백업 복원·앱 롤백 | DB/배포 담당 | [합성 백업 복원 확인](https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE/issues/777#issuecomment-6813443635). 실제 백업·롤백 훈련 필요 |
| 필수 알림 발화·해결 | 모니터링/당번 | 합성 검사와 실제 전달 시험을 구분해 PR에 기록 |

### 복구 훈련에서 남길 기록

- DB 담당은 백업 시각과 승인된 격리 환경을 기록합니다. 복원 시작·종료 시각, 대표 테이블 건수, 필수 인덱스와 읽기 확인 결과를 남깁니다. 업무 데이터와 인증정보는 기록에서 제외합니다.
- 배포 담당은 직전 정상 이미지와 DB 마이그레이션 호환을 확인합니다. 격리 환경에서 해당 이미지를 띄워 준비 상태·대표 GET·오류율을 확인하고 소요 시간을 남깁니다.
- 인프라 담당은 다음 예정된 전체 기동에서 기동 전후 CPU·MemAvailable·OOM과 Pod 재시작 횟수를 기록합니다.

## 업무 지표 공통 규칙안

`kidly_<도메인>_<대상>_<단위>` 이름을 사용하고 건수·상태·대기시간을 측정합니다.
주문·회원 ID, 이메일, URL의 가변 ID, 로그 본문을 라벨에 넣지 않습니다.
도메인 담당이 자기 코드에 구현하며, 다른 도메인의 업무 로직이나 정산 #730을 중복 구현하지 않습니다.

## 화면 사용

- 서비스 오류: 운영 상태와 사용자 영향부터 확인합니다.
- 서버: 호스트 전체 자원입니다. dev/prod 자원으로 나눠 읽지 않습니다.
- JVM·Spring Boot: 환경→서비스→인스턴스를 고르고 필요한 상세만 펼칩니다. 지표를 제공하지 않는 서비스는 빈 패널이 있을 수 있습니다.
- 로그: 환경·서비스·시간을 좁히고 traceId로 한 요청을 찾습니다. 원문을 PR·알림·AI에 복사하지 않습니다.

연결: [환경 운영 상세](operations.md) · [로그 검색](logs.md) · [구성·제약](architecture.md)
