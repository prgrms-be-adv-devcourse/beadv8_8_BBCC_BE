# Codex 메트릭 조회

**별도 Grafana 조직의 Prometheus만 읽습니다.** Loki·기존 조직의 대시보드/알림·설정 변경은 허용하지 않습니다. 메트릭 조회 결과는 연결한 OpenAI로 전달될 수 있습니다.

## 경계

- Grafana `Kidly MCP Metrics` 조직, Viewer 서비스 계정, 만료 7일. 자동 연장 없음
- datasource는 기존 Traefik의 조회 경로만 사용. query/range/metadata/labels/series/buildinfo만 GET·POST 허용
- write·admin·알 수 없는 경로·다른 메서드는 Traefik 내장 noop에서 거부해 Prometheus로 전달하지 않음
- 준비된 Grafana Pod 한 개의 실제 IP `/32`만 허용. 전달 헤더는 접근 판단에 사용하지 않음
- 배포 시 먼저 기본 거부, Grafana 준비 후 `update_boundary.py`로 IP 갱신. 실패하면 거부 유지
- 기존 Alloy remote write·앱·DB 설정은 변경하지 않음. 새 이미지·데몬·공개 포트 없음

## 설치 순서

1. 경계를 적용하고 아래 검증을 통과시킵니다. `bootstrap.py ensure`로 조직·datasource·Viewer를 준비합니다. 관리자 기본 조직은 바꾸지 않습니다.
2. 공식 v2.2.0 macOS arm64 배포본을 공급자 checksum과 `policy.json`의 SHA-256으로 확인합니다. PC의 `~/.local/share/kidly-grafana-mcp/`에 바이너리·`launch.py`·`policy.json`을 둡니다.
3. Codex에는 실행기를 **enabled=false**로 등록합니다. `connection.json`에는 조직·계정·토큰 ID·만료·바이너리 hash·검증 상태만 둡니다. 토큰 값은 넣지 않습니다.
4. 승인된 관리자가 비공개 SSH 파이프로 7일 토큰을 발급해 `store_token.py`의 stdin으로만 전달합니다. 직접 터미널 출력·인자·파일·환경 설정에 복사하지 않습니다. 저장 실패 시 발급한 ID로 즉시 폐기합니다.
5. Viewer 권한과 실제 공식 MCP 조회·거부 시험 후에만 검증 상태와 Codex의 enabled를 켭니다. macOS Keychain의 토큰은 실행기 내부에서만 읽어 자식 프로세스 환경으로 전달합니다.

클라이언트는 `--disable-write --enabled-tools=datasource,prometheus`를 쓰며 조직 전환·URL override·교차 출처 redirect·TLS 검증 생략을 끕니다. 저장 시계열 조회 창은 1시간, 반환 지점은 시계열당 241개로 제한하고 HTTP timeout은 10초입니다. 이 클라이언트 옵션은 서버 접근 경계를 대체하지 않습니다.

## 검증

- 관리 호스트에서 `KUBECONFIG=... python3 check_boundary.py --via-grafana-pod`: 정상 조회 7종, write/admin/unknown/변경 메서드 11종 거부
- 노드 원본 및 Grafana IP를 위조한 전달 헤더도 403인지 확인
- 실제 Viewer로 설정 쓰기 403, 다른 조직·Loki·기존 datasource의 UID/숫자 경로 접근 거부
- 공식 MCP initialize/tools/list 후 instant·range·metadata 조회, 2시간 저장 시계열 쿼리 거부 확인
- 로컬 회귀 검사: `python3 -m unittest discover -s deploy/monitoring/tests -p 'test_mcp_boundary.py'`

## Grafana Pod 교체 후 정상 조회가 403일 때

배포 밖에서 Pod가 교체되어 IP가 바뀌면 MCP는 새 Pod에서 접근할 수 없어 조회가 막힙니다. IP 허용은 갱신 시점의 주소 제한이며 지속적인 Pod 신원 인증은 아닙니다. 옛 IP가 다른 내부 Pod에 재할당될 수 있으므로, 교체 후에는 사용 전에 재동기화합니다. 이 경우에도 조회 외 경로 거부는 유지되어 쓰기·관리·Loki 접근은 열리지 않습니다.

GitHub Actions → **monitoring-deploy → Run workflow**로 재동기화할 수 있습니다. 앱·DB는 재기동하지 않지만 수집기는 잠시 재시작합니다. 관리자는 `update_boundary.py`만 실행할 수도 있습니다. 이후 `check_boundary.py --via-grafana-pod`를 다시 확인합니다. 조회를 복구한다고 CIDR 전체를 허용하거나 Viewer 권한을 올리지 않습니다.

## 중단·만료

Codex의 `kidly_grafana`를 끄고 `bootstrap.py revoke-token --token-id <ID>`로 폐기한 뒤 해당 Keychain 항목을 지웁니다. 만료 뒤 자동 재발급하지 않습니다. 다시 승인된 절차로 검증·발급합니다. 조직은 기존 운영 조직과 분리되어 있고 운영 알림은 기존 공간에서 계속 동작합니다.

[공식 MCP v2.2.0](https://github.com/grafana/mcp-grafana/releases/tag/v2.2.0) · [Codex MCP 설정](https://developers.openai.com/codex/mcp) · [Grafana 서비스 계정 API](https://grafana.com/docs/grafana/latest/developer-resources/api-reference/http-api/serviceaccount/)
