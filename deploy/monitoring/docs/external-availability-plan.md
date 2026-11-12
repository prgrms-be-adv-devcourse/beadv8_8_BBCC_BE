# 외부 가용성 확인

GitHub Actions에서 **external-observation → Run workflow → dev**를 실행합니다.
현재 단일 운영 환경의 수집 라벨은 `dev`이며, Grafana에는 **운영**으로 표시됩니다.

| 확인 대상 | 실패 시 첫 확인 |
| --- | --- |
| 앱 health | 앱 진입 경로와 서비스 상태 |
| 대표 상품 GET | Gateway 라우팅과 상품 서비스 |
| Grafana health | Grafana 및 Grafana 자체 DB 상태 |
| TLS | 각 호스트 인증서 검증 결과와 잔여일 |

워크플로는 상태 코드·응답 시간·TLS 잔여일을 남깁니다. 응답 본문·헤더·인증정보는 기록에서 제외합니다.
같은 워크플로를 다시 실행해 실패한 경로의 정상 응답을 확인합니다.
[최근 외부 수동 실행](https://github.com/prgrms-be-adv-devcourse/beadv8_8_BBCC_BE/actions/runs/43150620655)에서 HTTP 200 세 건과 TLS 잔여 66일을 확인했습니다.

## 상시 감시 결정

- 현재 AWS 자동 시작·정지 정책을 유지합니다. 정기 호출과 외부 Slack 통보는 보류 중입니다.
- 모니터링 서버가 중단되면 Grafana 내부 알림도 멈춥니다. 상시 감시는 서버 외부에서 실행하고, 운영시간·확인 주기·연속 실패 기준·수신 담당자를 정합니다.
- 운영 경로 중단 없이 시험 URL에서 실패·복구 통보를 검증하고 결과를 #987에 기록합니다.
