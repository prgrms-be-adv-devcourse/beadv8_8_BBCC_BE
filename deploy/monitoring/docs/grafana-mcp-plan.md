# Grafana MCP 읽기 전용 연결안 — 도입 승인 대기

MCP는 AI가 기존 화면·지표를 조회하도록 돕습니다. 상시 감시·Slack 알림·장애 대응 절차는 별도로 유지합니다.
새 도구 설치, 서비스 계정 발급, 토큰 발급·AI 연결은 아직 하지 않았습니다.

| 항목 | 제안 |
| --- | --- |
| 도구 | 공식 `grafana/mcp-grafana` **v2.2.0**, 승인 후 checksum 검증·버전 고정 |
| 실행 위치 | 개발자 PC, stdio. EC2에 별도 데몬·공개 포트 추가 없음 |
| 대상 | 기존 `https://grafana.kidly.kro.kr` 한 곳 |
| 권한 | 전용 서비스 계정의 Viewer부터 검증. 관리자 비밀번호·Editor·Admin 사용 금지 |
| 쓰기 차단 | `--disable-write` + Grafana 조회 권한, 두 겹으로 제한 |
| 기본 데이터 | 대시보드 요약·Prometheus·알림 조회. Loki·Assistant·관리 도구는 기본 제외 |
| 비용 | OSS 자체 Apache-2.0. AI 사용·PC 실행·기존 서버 자원 비용까지 무료라는 뜻은 아님 |

## 승인 후 연결

1. 실제 v2.2.0 도움말에서 조회에 필요한 도구 범주 이름과 `--enabled-tools` 허용 목록을 확인합니다. 기본 전체 도구를 켜지 않습니다.
2. 서비스 계정에는 우선 Viewer를 부여하고 필요한 조회가 가능한지 확인합니다. OSS의 역할이 필요한 datasource만 세밀하게 제한할 수 있는지 확인합니다. 부족하다고 Editor로 올리지 않습니다.
3. 만료가 있는 토큰을 OS 보안 저장소 또는 승인된 환경 주입으로 전달합니다. 토큰을 저장소·스크린샷·명령 인자·PR·디버그 로그에 넣지 않습니다.
4. `GRAFANA_URL`, `GRAFANA_SERVICE_ACCOUNT_TOKEN`을 실행 환경으로 전달하고 stdio·`--disable-write`·필요 도구 허용 목록으로 실행합니다. URL override·교차 출처 redirect·debug·인자 tracing은 켜지 않습니다.
5. dev 메트릭·대시보드 요약·알림 조회 성공과 쓰기 도구 미노출/거부를 확인합니다. 실제 대시보드나 규칙을 파괴하는 시험은 하지 않습니다.
6. 해제 시 프로세스를 종료하고 서비스 계정 토큰을 폐기합니다.

## 로그 조회는 별도 승인

Viewer·읽기 전용이라고 개인정보 반출을 막아 주지는 않습니다. 조회 결과는 연결한 AI 제공자에게 전달될 수 있습니다.
Loki를 켤 때는 제공자·처리 지역·보관 정책과 반출 범위를 승인받습니다. 기본 후보는 dev 한 서비스·최근 15분·최대 20건이며, 본문·헤더·개인 식별값은 보내지 않습니다. AI나 도구가 임의로 이 범위를 넓히지 않습니다.

공식 근거: [v2.2.0 릴리스](https://github.com/grafana/mcp-grafana/releases/tag/v2.2.0), [공식 사용법·권한·라이선스](https://github.com/grafana/mcp-grafana#readme).
