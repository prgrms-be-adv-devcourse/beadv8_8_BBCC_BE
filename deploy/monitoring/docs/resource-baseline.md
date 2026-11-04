# 서버 자원 점검 — 2026-11-04

아래 표는 1차 읽기 전용 조회 시점의 기록입니다. 당시 dev만 실행 중이었으며, prod 기동·부하 시험·자원 제한 변경은 하지 않았습니다.

| 관측 | 앱 서버 | 모니터링 서버 |
| --- | --- | --- |
| 실제 메모리 | 7,819MiB | 3,835MiB |
| 현재 MemAvailable | 3,613MiB (약 46%) | 751MiB (약 20%) |
| 최근 24시간 최소 MemAvailable 비율 | 41.74% | 17.56% |
| 루트 디스크 사용 | 약 23% | 약 17% |
| 루트 inode 사용 | 약 1% | 약 1% |
| 관측된 최근 24시간 OOM kill 증가 | 0 | 0 |
| swap | 없음 | 없음 |

1차 조회 당시 앱 서버의 dev 컨테이너 10개는 모두 실행 중이며, 현재 컨테이너의 OOMKilled=false·재시작=0입니다.
이는 이미 교체·삭제된 컨테이너의 과거 OOM까지 부정하는 결과는 아닙니다.
Alloy의 메모리 상한은 없으며 현재 사용량은 약 167MiB입니다.

## 동시 운영 판단

- dev 컨테이너 working set 합계의 최근 24시간 최고값은 약 **3.33GiB**입니다(1분 간격 조회).
- 배포 전후의 같은 이름 컨테이너를 단순 합산하면 약 5.72GiB로 부풀려집니다. 30초 이내 표본만 고르고 호스트·이름별 중복을 제거했습니다. 순간 피크는 놓칠 수 있습니다.
- 정산·상품의 prod 활성화 시점에는 환경별 상한이 6,976MiB였습니다. 이후 #814에서 자녀 프로필 서비스가 제외됐으므로 최신 구성으로 다시 합산해야 합니다. 과거 실측 시점과 현재 서비스 목록을 섞지 않습니다.
- 상한은 예약량이 아니므로 합계만으로 기동 불가를 판단하지 않습니다. 반대로 현재 dev 사용량만으로 두 환경의 부하·기동 피크까지 안전하다고 판단할 수도 없습니다.
- **동시 운영 가능 여부는 미확정**입니다. 순차 기동 후 두 환경이 안정화된 상태와 실제 부하에서 전체 MemAvailable·서비스 사용량·OOM을 함께 확인해야 합니다.
- 여유가 부족하면 한 환경만 운영할 수 있습니다. #772의 stop/resume으로 운영 의도를 기록하며, 서버 증설·swap·상한 변경은 측정 결과를 보고 별도로 결정합니다.

## 자원 알림

| 알림 | 기준 | 첫 확인 |
| --- | --- | --- |
| 메모리 여유 부족 | MemAvailable 10% 미만, 5분 | 추가 기동 보류·컨테이너 사용량 확인 |
| 루트 디스크 부족 | 사용률 80% 초과, 10분 | 로그·이미지·데이터 보관량 확인 |
| inode 부족 | 사용률 90% 초과, 10분 | 작은 파일 급증 경로 확인 |
| OOM 종료 | 최근 5분 OOM kill 증가 | 커널 기록·컨테이너 OOMKilled 확인 |

두 호스트를 instance로 구분하고, 최신 호스트 표본이 60초 이상 끊기면 자원 경보를 새로 만들지 않습니다.
데이터 부재는 정상 판정이 아니며 수집 장애 감지는 #772, 모니터링 서버 전체 중단의 외부 감시는 #775 범위입니다.
18시 자동 정지 정책 자체는 변경하지 않습니다. 앱 환경을 중지해도 호스트가 실행 중이면 호스트 자원은 계속 감시합니다.
실제 자원을 고갈시키지 않고 임시 임계값으로 발화·복구를 검증한 뒤에만 운영 반영합니다.

## 재확인

Prometheus에서 아래를 조회하고 서버의 `free -m`, `df -h /`, `df -i /`, `docker stats`와 대조합니다.

```promql
min_over_time((100 * node_memory_MemAvailable_bytes{job="integrations/unix"} / node_memory_MemTotal_bytes{job="integrations/unix"})[24h:1m])
max_over_time((sum(max by (instance, name) (container_memory_working_set_bytes{job="integrations/cadvisor",name=~"dev-.*"} and (time() - timestamp(container_memory_working_set_bytes{job="integrations/cadvisor",name=~"dev-.*"}) < 30))))[24h:1m])
increase(node_vmstat_oom_kill{job="integrations/unix"}[24h])
```

`ruby deploy/monitoring/tests/check-resources.rb /path/to/promtool`で実際の規則の継続時間・復旧・ホスト分離・収集中断を検査します。
