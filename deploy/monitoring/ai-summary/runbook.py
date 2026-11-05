"""Local, deterministic explanations. Findings are candidates, never automatic actions."""
from urllib.parse import urlencode

PRIORITY = {'critical': 0, 'warning': 1, 'unknown': 2, 'info': 3}
LABEL = {'critical': '우선 확인', 'warning': '확인 필요', 'unknown': '판단 보류', 'info': '참고'}


def explain(facts, statuses):
    index = {(f['metric'], f['env'], f['service']): f for f in facts}
    findings = []

    def get(metric, env=None, job=None):
        return index.get((metric, env, job))

    def add(code, severity, title, observation, cause, checks, recommendation, refs, dashboard, env=None, job=None):
        params = {'orgId': 1}
        if env:
            params['var-env'] = env
        if job:
            params['var-service'] = job
        findings.append({'code': code, 'severity': severity, 'title': title,
                         'observation': observation, 'hypothesis': cause,
                         'checks': checks, 'recommendation': recommendation,
                         'evidence': [f['id'] for f in refs if f is not None],
                         'url': '/d/' + dashboard + '?' + urlencode(params)})

    resources = [
        ('memory_available_pct', lambda v: v < 10, '메모리 여유 부족',
         '컨테이너/JVM 사용량 증가 또는 여러 서비스 동시 기동이 후보입니다. 원인 서버는 아직 특정하지 않았습니다.',
         ['서버 화면에서 메모리 여유가 낮은 서버를 고르세요', '컨테이너 메모리·최근 배포 시각·OOM 기록을 비교하세요'],
         '추가 기동을 잠시 피하고 원인 컨테이너를 확인하세요. 임의 종료 대신 운영 환경 조정을 팀과 결정하세요.'),
        ('disk_used_pct', lambda v: v > 80, '디스크 사용률 높음',
         '로그·이미지·DB/Kafka 데이터 증가가 후보입니다. 경로별 사용량 확인이 필요합니다.',
         ['서버 화면에서 루트 디스크가 찬 서버를 확인하세요', '해당 서버의 경로별 사용량과 로그/이미지 보관 정책을 확인하세요'],
         '보관 정책이나 용량을 검토하세요. DB·Kafka 볼륨을 삭제하지 마세요.'),
        ('cpu_used_pct', lambda v: v > 90, 'CPU 사용률 높음',
         '최근 배포·트래픽·배치 작업이 후보입니다. 특정 앱 때문인지는 아직 알 수 없습니다.',
         ['서버별 CPU와 컨테이너 사용량을 비교하세요', '같은 시간의 배포·트래픽·배치 실행을 확인하세요'],
         '원인 작업부터 확인하고 추가 부하 작업을 미루세요. 필요하면 실행 시간 분산을 검토하세요.'),
    ]
    missing = []
    for metric, threshold, title, cause, checks, advice in resources:
        fact = get(metric)
        if fact is None:
            missing.append(metric)
        elif threshold(fact['value']):
            add(metric, 'warning', title, f"수집된 서버 중 최악 관측값: {fact['value']:g}% (현재 관측; 지속 시간 미판정)",
                cause, checks, advice, [fact], 'kidly-server')
    oom = get('oom_30m')
    if oom and oom['value'] > 0:
        add('oom', 'critical', '최근 메모리 부족 종료 기록', f"최근 30분 OOM 종료 증가 추정 {oom['value']:g}건 (수집된 서버 합계)",
            '호스트 메모리 압박이 있었습니다. 현재 여유가 회복돼도 어떤 프로세스가 종료됐는지 확인해야 합니다.',
            ['서버별 OOM 기록과 컨테이너 OOMKilled/재시작 여부를 확인하세요', '같은 시각의 배포·기동 수와 메모리 사용량을 비교하세요'],
            '종료 대상과 메모리 원인을 확인한 뒤 복구하세요. 재시작만으로 해결됐다고 판단하지 마세요.', [oom], 'kidly-server')
    if missing:
        add('resource-missing', 'unknown', '서버 자원 일부 판단 불가', '신선한 지표 없음: ' + ', '.join(missing),
            '서버 중지·수집 지연 또는 수집기/지표 설정 누락을 구분해야 합니다.',
            ['서버와 Alloy 운영 여부를 확인하세요', '서버 화면의 수집 시각·수집기 상태를 확인하세요'],
            '지표 수집을 먼저 확인하세요. 값이 없다는 이유로 여유가 있다고 판단하지 마세요.', [], 'kidly-server')

    for env, status in statuses.items():
        inv = [f for f in facts if f['metric'] == 'inventory' and f['env'] == env]
        enabled = get('environment', env)
        if status == '의도적 중지':
            add('stopped-' + env, 'info', env + ' 환경은 의도적 중지', '환경 운영 설정이 중지입니다.',
                '이 환경의 앱 지표 부재는 장애 근거로 사용하지 않습니다.',
                ['다시 운영할 필요가 있을 때만 environment-operation 이력을 확인하세요'],
                '재개가 필요하면 팀과 합의해 resume을 실행하세요. 자동 재시작하지 않습니다.', [enabled], 'kidly-http', env)
            continue
        if status == '중지·재개 작업 중':
            add('transition-' + env, 'info', env + ' 환경 전환 중', '중지·재개 작업 표시가 있습니다.',
                '배포/환경 전환 중 일시적으로 수집이 비어 있을 수 있습니다. 오래 남으면 실패한 작업일 수 있습니다.',
                ['environment-operation 실행 상태와 종료 결과를 확인하세요'],
                '작업 종료 후에도 전환 표시가 남으면 실패 단계를 확인하세요. 상태 파일을 지워 우회하지 마세요.',
                [get('transition', env)], 'kidly-http', env)
            continue
        if status.startswith('확인 불가'):
            add('inventory-' + env, 'unknown', env + ' 운영 대상 판단 불가', status,
                '운영 대상 미등록 또는 모니터링 서버의 inventory 수집 중단이 후보입니다.',
                ['실제로 운영하려는 환경인지 먼저 확인하세요', 'environment-operation 이력과 inventory 수집 상태를 확인하세요'],
                '운영 대상이 확인되기 전에는 DOWN/정상 판정을 하지 않습니다.', [enabled], 'kidly-http', env)
            continue
        expected = sorted(f['service'] for f in inv if f['value'] == 1)
        gaps = []
        for job in expected:
            up = get('up', env, job)
            expected_fact = get('inventory', env, job)
            if up is None or up['value'] != 1:
                down = up is not None
                add('target-' + env + job, 'critical', env + ' ' + job + (' 수집 실패' if down else ' 수집 누락'),
                    '운영 대상으로 등록되어 있지만 ' + ('현재 up=0입니다.' if down else '신선한 up 지표가 없습니다.'),
                    '앱 중단·기동 실패·actuator 연결 문제' if down else 'targets 등록 누락·Alloy 전송 중단·서버 연결 문제가 후보입니다.',
                    ['환경 전환/배포 중인지 Actions 이력을 확인하세요', '해당 컨테이너 상태와 /actuator/prometheus 수집 경로를 확인하세요', '여러 서비스가 함께 빠지면 Alloy와 서버 연결부터 확인하세요'],
                    '의도적 중지라면 environment-operation을 사용하세요. 실제 실패라면 기동 로그/직전 배포를 확인한 후 복구를 결정하세요.',
                    [expected_fact, up], 'kidly-http', env, job)
                continue
            requests = get('requests_30m', env, job)
            errors = get('errors_30m', env, job)
            duration = get('duration_sum_30m', env, job)
            pending = get('hikari_pending', env, job)
            util = get('hikari_utilization', env, job)
            count = requests['value'] if requests else 0
            slow = duration is not None and count >= 20 and duration['value'] / count > 1
            blocked = pending is not None and pending['value'] > 0
            if slow or blocked or (util and util['value'] >= .9):
                correlated = slow and blocked
                observation = []
                if slow:
                    observation.append(f"30분 평균 응답 {duration['value']/count:.2f}초 / 요청 약 {count:.0f}건 (p95 아님)")
                if pending:
                    observation.append(f"현재 DB 연결 대기 {pending['value']:g}")
                if util:
                    observation.append(f"현재 연결 풀 사용률 {util['value']*100:.1f}%")
                add('latency-db-' + env + job, 'warning', env + ' ' + job + (' 응답 지연과 DB 대기 동반' if correlated else ' 응답/DB 연결 점검'),
                    ' · '.join(observation),
                    '긴 트랜잭션·느린 쿼리·DB 응답 지연이 후보입니다. 두 지표만으로 원인을 확정할 수 없습니다.' if blocked else '앱 처리·외부 의존성·부하 증가가 후보입니다. DB 대기 지표와 최근 배포를 함께 확인해야 합니다.',
                    ['응답·DB 연결 화면에서 해당 서비스의 연결 대기와 사용률을 확인하세요', '지연 시작 시각과 최근 배포·트래픽을 비교하세요', '해당 시간의 느린 쿼리/트랜잭션과 서비스 로그를 확인하세요'],
                    '연결 수부터 늘리지 말고 오래 점유하는 요청과 DB 지연부터 확인하세요. 변경은 담당자와 검토하세요.',
                    [requests, duration, pending, util], 'kidly-db-latency', env, job)
            if errors and count >= 20 and errors['value'] >= 5 and errors['value'] / count > .05:
                add('errors-' + env + job, 'warning', env + ' ' + job + ' 5xx 증가',
                    f"30분 오류 약 {errors['value']:.0f}건 / 요청 약 {count:.0f}건 ({100*errors['value']/count:.1f}%)",
                    '최근 변경·의존 서비스 실패·처리 예외가 후보입니다.' + (' Gateway에는 하위 서비스 오류도 포함됩니다.' if job == 'kidly-gateway' else ''),
                    ['오류 화면에서 같은 시간의 서비스별 오류를 비교하세요', '최근 배포 이후 시작됐는지 확인하세요', '선택한 환경·서비스 로그에서 해당 시간의 예외 유형을 확인하세요'],
                    '서비스 간 같은 요청을 합산하지 마세요. 배포와 인과가 확인되면 담당자와 롤백을 검토하세요.',
                    [requests, errors], 'kidly-http', env, job)
            if requests is None or count < 20 or errors is None or duration is None:
                gaps.append(job)
        if not expected:
            gaps.append('운영 대상 없음')
        if gaps:
            add('http-coverage-' + env, 'unknown', env + ' HTTP 판단 범위 제한',
                '시계열 누락 또는 최근 30분 요청 20건 미만: ' + ', '.join(gaps),
                '트래픽 부족과 HTTP 계측/수집 누락은 지표만으로 구분할 수 없습니다.',
                ['서비스별 요청량과 actuator 계측 여부를 확인하세요'],
                '오류율/평균 응답으로 정상 여부를 단정하지 마세요. 저트래픽 서비스는 실제 요청 확인을 병행하세요.',
                [], 'kidly-http', env)
        lag = get('lag', env)
        if lag and lag['value'] > 1000:
            add('lag-' + env, 'warning', env + ' Kafka 처리 대기 점검', f"현재 환경 전체 lag 합계 {lag['value']:g}",
                '소비자 중단·처리 속도 저하·일시적 유입 증가가 후보입니다. 한 시점의 합계로 증가 추세를 판단할 수 없습니다.',
                ['Kafka 화면에서 어떤 그룹·토픽이 밀리는지 확인하세요', '해당 소비자 서비스 상태·최근 배포·처리 로그를 확인하세요', '일시적 증가인지 시간 추이로 확인하세요'],
                '실패/지연 원인을 먼저 확인하세요. offset 초기화·메시지 삭제·임의 재발행은 하지 마세요.',
                [lag], 'kidly-kafka', env)
        elif lag is None:
            add('lag-missing-' + env, 'unknown', env + ' Kafka 대기량 판단 불가', '신선한 consumer lag 집계가 없습니다.',
                'exporter 중단·소비자 그룹 미등록 또는 exporter 수집 누락을 구분해야 합니다.',
                ['Kafka 화면에서 exporter UP과 그룹 지표를 확인하세요'],
                '데이터가 없다는 이유로 lag=0으로 해석하지 마세요.', [], 'kidly-kafka', env)
    return {'findings': sorted(findings, key=lambda f: (PRIORITY[f['severity']], f['code']))}
