# Grafana MCP 읽기 전용 연결안

MCP는 AI가 기존 화면·지표를 조회하도록 돕습니다. 상시 감시·Slack 알림·장애 대응 절차는 별도로 유지합니다.
현재 #862에서 승인받은 메트릭 전용 연결을 구현합니다. 실제 경계·설치·해제 절차는 [연결 운영 안내](../mcp/README.md)를 봅니다. 기존 조직의 대시보드·알림과 Loki는 연결하지 않습니다.

| 항목 | 제안 |
| --- | --- |
| 도구 | 공식 `grafana/mcp-grafana` **v2.2.0**, 승인 후 checksum 검증·버전 고정 |
| 실행 위치 | 개발자 PC, stdio. EC2에 별도 데몬·공개 포트 추가 없음 |
| 대상 | 기존 `https://grafana.kidly.kro.kr` 한 곳 |
| 권한 | 전용 서비스 계정의 Viewer부터 검증. 관리자 비밀번호·Editor·Admin 사용 금지 |
| 쓰기 차단 | `--disable-write` + Grafana 조회 권한, 두 겹으로 제한 |
| 기본 데이터 | 현재 연결은 Prometheus·datasource 메타데이터만. Loki·대시보드·알림·관리 도구 제외 |
| 비용 | OSS 자체 Apache-2.0. AI 사용·PC 실행·기존 서버 자원 비용까지 무료라는 뜻은 아님 |

## 승인 후 연결

1. 실제 v2.2.0 도움말에서 조회에 필요한 도구 범주 이름과 `--enabled-tools` 허용 목록을 확인합니다. 기본 전체 도구를 켜지 않습니다.
2. 아래의 datasource 격리 방식을 먼저 확정한 뒤 전용 Viewer 계정을 생성해 필요한 조회가 가능한지 확인합니다. OSS Viewer 자체로 datasource별 제한이 된다고 가정하지 않으며, 부족하다고 Editor로 올리지 않습니다.
3. 만료가 있는 토큰을 OS 보안 저장소 또는 승인된 환경 주입으로 전달합니다. 토큰을 저장소·스크린샷·명령 인자·PR·디버그 로그에 넣지 않습니다.
4. `GRAFANA_URL`, `GRAFANA_SERVICE_ACCOUNT_TOKEN`을 실행 환경으로 전달하고 stdio·`--disable-write`·필요 도구 허용 목록으로 실행합니다. URL override·교차 출처 redirect·debug·인자 tracing은 켜지 않습니다.
5. dev 메트릭·대시보드 요약·알림 조회 성공과 쓰기 도구 미노출/거부를 확인합니다. 실제 대시보드나 규칙을 파괴하는 시험은 하지 않습니다.
6. 해제 시 프로세스를 종료하고 서비스 계정 토큰을 폐기합니다.

## 로그 조회는 별도 승인

Viewer·읽기 전용이라고 개인정보 반출을 막아 주지는 않습니다. 조회 결과는 연결한 AI 제공자에게 전달될 수 있습니다.
Loki를 켤 때는 제공자·처리 지역·보관 정책과 반출 범위를 승인받습니다. 기본 후보는 dev 한 서비스·최근 15분·최대 20건이며, 본문·헤더·개인 식별값은 보내지 않습니다. AI나 도구가 임의로 이 범위를 넓히지 않습니다.

공식 근거: [v2.2.0 릴리스](https://github.com/grafana/mcp-grafana/releases/tag/v2.2.0), [공식 사용법·권한·라이선스](https://github.com/grafana/mcp-grafana#readme).

## 리뷰 반영: Viewer와 로그 접근의 한계

현재 이미지는 Grafana 12.4.12입니다. 공식 문서상 datasource별 조회 권한은 Enterprise/Cloud 기능이며, OSS Viewer는 접근 가능한 조직의 datasource에 임의 쿼리를 보낼 수 있습니다.
따라서 **Viewer + Loki 도구 제외만으로 메트릭 전용 접근을 보장하지 않습니다.** 도구 허용 목록은 추가 제한이며 datasource 권한 경계가 아닙니다. 다른 조회 도구의 우회 가능성을 검증하지 않은 상태에서 토큰을 발급·연결하지 않습니다.

향후 연결 승인 시에는 Prometheus만 있는 별도 조직 등 실제 datasource 격리 방식을 먼저 정합니다. 별도 조직을 쓰면 기존 조직의 대시보드·알림이 그대로 조회되지 않는 한계도 함께 검토합니다. 이를 해결한다고 Editor/Admin으로 올리거나 Loki 접근을 묵인하지 않습니다.
사용자 승인 후 #862에서 Prometheus만 있는 별도 조직과 Viewer 계정을 구성합니다. 기존 Traefik의 서버 경계가 조회 API만 전달하며, 다른 조직의 datasource·Loki·기존 대시보드·알림은 이 연결에 포함하지 않습니다.

토큰은 최초 시험 **7일**, 이후 운영도 **30일 이하** 만료를 제안합니다. 종료·분실·담당자 변경 시 즉시 폐기하고 새 토큰을 발급하며 값을 로그·PR에 남기지 않습니다.
근거: [공식 datasource 관리·Viewer 조회 범위](https://grafana.com/docs/grafana/latest/administration/data-source-management/), [공식 역할·권한](https://grafana.com/docs/grafana/latest/administration/roles-and-permissions/).
