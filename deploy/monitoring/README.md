# 모니터링

**[Grafana 열기](https://grafana.kidly.kro.kr)** · 장애 알림: Slack `#4팀-운영이슈`

| 보고 싶은 것 | Grafana → Kidly 폴더 |
| --- | --- |
| 서비스 상태·오류 | **Kidly 서비스 오류** → 환경·시간 선택 |
| AI 분석 메모 | **Kidly AI 운영 분석** (게시 연결 전에는 메모 없음) |
| 오류 원인 | **Kidly 로그** → 서비스 선택 |
| 메모리·디스크·CPU | **서버** → 서버 선택 |

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
