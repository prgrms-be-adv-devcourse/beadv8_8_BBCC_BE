# 모니터링

**[Grafana 열기](https://grafana.kidly.kro.kr)** · 장애 알림: Slack `#4팀-운영이슈`

| 보고 싶은 것 | Grafana → Kidly 폴더 |
| --- | --- |
| 전체 서비스가 괜찮은지 | **Kidly 운영 홈** → 맨 위 서비스 상태판(빨강 · 주황 칸이 있는 줄을 누르면 아래 화면으로 이동) |
| 서비스 하나 자세히 | **Kidly 서비스 상세** → 서비스 선택(HTTP · JVM · DB 커넥션 풀 · Kafka · 재시작 표시 · 트레이스(게이트웨이 · 주문)) |
| 서비스 상태·오류 | **Kidly 서비스 오류** → 환경·시간 선택(오류가 난 API 표 포함) |
| 오류 원인 | **Kidly 로그** → 서비스 선택(자주 난 예외 · 예외 위치(우리 코드) 표 → ERROR 로그) |
| AI 분석 메모 | **Kidly AI 운영 분석** → 맨 위 '새 분석 실행'(1시간에 1번). 서비스 알림이 뜨면 자동 실행 |
| 부하 테스트 결과 | **Kidly 부하 테스트** → 테스트(testid) · 서비스 선택. 실행 방법: [부하 테스트 모니터링](docs/load-test-monitoring.md) |
| 메모리·디스크·CPU | **서버** → 서버 선택 |

평균 응답시간은 실시간 알림 스트림(SSE) 두 경로를 뺀 값입니다. Gateway는 경로 구분이 없어 스트림이 섞일 수 있습니다. 서버 p95는 없고(앱 히스토그램 미수집), 분위수는 오류 로그의 API 처리 시간(로그 기반)이나 k6 값으로 봅니다.

## 환경 끄기 / 켜기

GitHub Actions → **environment-operation** → **Run workflow**

- 환경: `dev` (prod 환경은 #1210 에서 걷어냈다)
- 동작: `stop` 끄기 / `resume` 켜기
- 완료 후 Grafana에서 상태 확인

**선택한 환경의 앱·DB·Kafka가 함께 꺼집니다. 데이터는 유지하고, 다른 환경은 건드리지 않습니다.**
중지한 환경의 앱 장애 알림은 멈추며, 자동 배포로 다시 켜지지 않습니다.
실패하면 Actions의 실패 단계를 확인합니다. 파일을 직접 지워 감시를 끄지 마세요.

## 설정 바꾸기

**저장소 수정 → PR → develop 머지 → 자동 배포**
Grafana 화면 수정은 유지되지 않습니다. 비밀번호·웹훅은 GitHub Secrets에서 관리합니다.

[설치·배포](docs/setup.md) · [중지·재개 상세](docs/operations.md) · [로그 검색](docs/logs.md) · [구성·제약](docs/architecture.md)
