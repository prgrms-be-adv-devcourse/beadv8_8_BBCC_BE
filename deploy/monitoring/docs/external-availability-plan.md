# 外부 접근 감시

**자동 감시: [Better Stack](https://incidents.betterstack.com/team/t638248/monitors)** · 무료 플랜 · 알림은 가입 계정 이메일.
기존 Grafana → Slack 알림은 그대로 사용합니다. Better Stack의 Slack 연결은 워크스페이스 무료 앱 한도로 제외했습니다.

| 감시 | 기준 |
| --- | --- |
| 운영 · 상품 조회 경로 | `https://api.kidly.kro.kr/api/v1/products?page=0&size=1` 접근 가능 여부. 상품 정확성 검사는 아님 |
| 운영 · Grafana 상태 | `https://grafana.kidly.kro.kr/api/health` 응답에 `"ok"` 포함. JSON 구조 검증이나 전체 업무 복구 보장은 아님 |

- Asia에서 **5분 간격** 확인. 실패·회복 확인 기간 각각 1분, 요청 제한 시간 10초입니다. 정확한 탐지 시간 보장은 아닙니다.
- 기존 서버 운영시간 09~18시 KST를 유지합니다. **매일 17:55~다음 날 09:15, Asia/Seoul** 유지보수로 야간 알림을 중지합니다.
- TLS 인증서 검증은 켜고 리다이렉트·쿠키 유지는 끕니다. 인증·회원정보·로그를 보내지 않습니다.
- Free 플랜이며 카드 등록·유료 업그레이드·서버 설치·배포가 없습니다. 인증서 만료 사전 알림은 유료 기능이므로 켜지 않습니다.
- 두 감시 모두 실제 자동 점검에서 Up을 확인했습니다. 시험 알림은 발송 완료 표시까지 확인했으며 **실제 메일 수신은 사용자 확인 대기**입니다.

## 수동 진단

**GitHub Actions → external-observation → Run workflow → diagnostic**. 운영 라벨은 `dev`입니다. GitHub 예약 실행은 실동작을 확인하지 못해 제거했고, 중복 감시 방지를 위해 `EXTERNAL_OBSERVATION_ENABLED=false`로 두었습니다. 이 변수는 현재 수동 실행에는 영향을 주지 않습니다.

| 검사 | 해석 |
| --- | --- |
| Gateway 자체 응답 | 고정 미등록 경로의 HTTP 404 + `code=NOT_FOUND`. 의도한 정상 검사이며 업무 서비스·DB는 호출하지 않음 |
| 상품 GET | HTTP 200. Gateway가 정상이고 상품만 실패하면 검색·Elasticsearch부터 확인 |
| Grafana health | HTTP 200 + `database=ok`. 실패하면 모니터링 서버·엣지·Grafana 확인 |
| TLS | 호스트 인증서 검증·잔여일 확인. 자동 만료 사전 통보를 대신하지 않음 |

`observe-notify`는 운영시간 안에서 기존 Slack으로 상태 변화를 1회 통보하고, `notify-test`는 **[시험]** 메시지 2개를 보냅니다. 기본 `diagnostic`은 조회만 합니다. 응답 본문·인증정보는 기록하지 않으며 자동 재시작·복구는 하지 않습니다.

## 알림을 받으면

메일의 감시 이름·발생 시각·HTTP 상태를 확인하고 수동 진단으로 실패 경로를 좁힙니다. Grafana가 외부에서 안 열리면 내부 Slack 알림도 멈췄을 수 있습니다. 메일 스팸 처리·외부 감시 서비스 장애는 별도 한계입니다. 운영시간을 바꾸면 Better Stack의 유지보수 시간도 함께 바꿉니다.
