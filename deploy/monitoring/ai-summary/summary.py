#!/usr/bin/env python3
"""Local monitoring runbooks. No external API, credentials or auto-remediation."""
import argparse
import datetime as dt
import fcntl
import html
import json
import math
import os
from pathlib import Path
import sys
import tempfile
import time
import urllib.parse
import urllib.request

from runbook import explain, LABEL

SERVICES = ('gateway', 'search', 'recommend', 'member', 'payment', 'product',
            'order', 'community', 'settlement', 'notification')
JOBS = {'kidly-' + name for name in SERVICES}
MATCH = '|'.join(sorted(JOBS))
SELECT = 'job=~"' + MATCH + '",uri!~"/actuator.*"'
QUERIES = {
    'inventory': 'max by (env,app_job) (kidly_app_expected and (time()-timestamp(kidly_app_expected)<60))',
    'environment': 'max by (env) (kidly_environment_enabled and (time()-timestamp(kidly_environment_enabled)<60))',
    'transition_age': 'max by (env) ((time()-kidly_environment_changed_timestamp_seconds) and (time()-timestamp(kidly_environment_changed_timestamp_seconds)<60))',
    'transition': 'max by (env) (kidly_environment_transition_active and (time()-timestamp(kidly_environment_transition_active)<60))',
    'up': 'min by (env,job) (up{job=~"' + MATCH + '"} and (time()-timestamp(up{job=~"' + MATCH + '"})<60))',
    'requests_30m': 'sum by (env,job) (increase(http_server_requests_seconds_count{' + SELECT + '}[30m]))',
    'errors_30m': 'sum by (env,job) (increase(http_server_requests_seconds_count{' + SELECT + ',status=~"5.."}[30m]))',
    'duration_sum_30m': 'sum by (env,job) (increase(http_server_requests_seconds_sum{' + SELECT + '}[30m]))',
    'hikari_utilization': 'max by (env,job) (hikaricp_connections_active / hikaricp_connections_max and (time()-timestamp(hikaricp_connections_active)<60))',
    'cpu_used_pct': 'max(100 * (1-avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m]))) and on (instance) (max by (instance) (time()-timestamp(node_cpu_seconds_total{mode="idle"}))<60))',
    'oom_30m': 'sum(increase(node_vmstat_oom_kill[30m]) and (time()-timestamp(node_vmstat_oom_kill)<60))',
    'hikari_pending': 'sum by (env,job) (hikaricp_connections_pending and (time()-timestamp(hikaricp_connections_pending)<60))',
    'lag': 'sum by (env) (kafka_consumergroup_lag and (time()-timestamp(kafka_consumergroup_lag)<60))',
    'memory_available_pct': 'min(100 * node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes and (time()-timestamp(node_memory_MemAvailable_bytes)<60))',
    'disk_used_pct': 'max(100 * (1-node_filesystem_avail_bytes{mountpoint="/",fstype!~"tmpfs|overlay"}/node_filesystem_size_bytes{mountpoint="/",fstype!~"tmpfs|overlay"}) and (time()-timestamp(node_filesystem_avail_bytes{mountpoint="/",fstype!~"tmpfs|overlay"})<60))',
}
MAX_RESPONSE = 1024 * 1024


def atomic(path, value, mode=0o600):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            os.fchmod(stream.fileno(), mode)
            stream.write(value)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError('redirect refused')


def request(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost') or parsed.port != 30090 or parsed.username or parsed.path != '/api/v1/query':
        raise ValueError('only local Prometheus queries are allowed')
    req = urllib.request.Request(url)
    with urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect).open(req, timeout=20) as response:
        raw = response.read(MAX_RESPONSE + 1)
    if len(raw) > MAX_RESPONSE:
        raise ValueError('response too large')
    return json.loads(raw)


def collect(base, now):
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost') or parsed.port != 30090 or parsed.username or parsed.query or parsed.path not in ('', '/'):
        raise ValueError('Prometheus must use localhost NodePort')
    result = {}
    for name, expr in QUERIES.items():
        query = urllib.parse.urlencode({'query': expr, 'time': now})
        data = request(base.rstrip('/') + '/api/v1/query?' + query)
        if data.get('status') != 'success' or data.get('warnings') or data.get('data', {}).get('resultType') != 'vector':
            raise ValueError('incomplete Prometheus response')
        result[name] = data['data']['result']
    return result


def project(raw):
    """Only fixed env/job names and finite numbers enter the local summary."""
    facts = []
    for name in QUERIES:
        seen = set()
        rows = raw.get(name, [])
        if not isinstance(rows, list) or len(rows) > 100:
            raise ValueError('invalid metric rows')
        for row in rows:
            labels = row['metric']
            env = labels.get('env')
            job = labels.get('app_job') if name == 'inventory' else labels.get('job')
            if name in ('memory_available_pct', 'disk_used_pct', 'cpu_used_pct', 'oom_30m'):
                env, job = None, None
            elif env not in ('dev', 'prod') or (name not in ('environment', 'transition', 'transition_age', 'lag') and job not in JOBS):
                continue
            value = float(row['value'][1])
            if not math.isfinite(value) or value < 0 or (name in ('inventory', 'environment', 'transition', 'up') and value not in (0, 1)):
                continue
            key = (env, job)
            if key in seen:
                raise ValueError('ambiguous aggregate')
            seen.add(key)
            facts.append({'id': 'f' + str(len(facts)), 'metric': name, 'env': env,
                          'service': job, 'value': round(value, 4)})
    return facts


def assessment(facts):
    """Missing and stopped targets never imply healthy services."""
    statuses = {}
    for env in ('dev', 'prod'):
        def values(metric):
            return [f for f in facts if f['env'] == env and f['metric'] == metric]
        enabled = values('environment')
        inventory = values('inventory')
        if not enabled:
            statuses[env] = '확인 불가: 운영 대상 정보 없음'
        elif enabled[0]['value'] == 0:
            statuses[env] = '의도적 중지'
        elif any(f['value'] == 1 for f in values('transition')):
            statuses[env] = '중지·재개 작업 중'
        elif not inventory:
            statuses[env] = '확인 불가: 운영 대상 정보 없음'
        else:
            expected = {f['service'] for f in inventory if f['value'] == 1}
            actual = {f['service']: f['value'] for f in values('up')}
            if not expected or any(job not in actual for job in expected):
                statuses[env] = '확인 필요: 운영 대상 수집 누락'
            elif any(actual[job] != 1 for job in expected):
                statuses[env] = '확인 필요: 수집 대상 DOWN'
            else:
                statuses[env] = '운영 대상 수집 중 · 서비스 정상 보장은 아님'
    return statuses



def stamp(epoch):
    return dt.datetime.fromtimestamp(epoch, dt.timezone(dt.timedelta(hours=9))).strftime('%m/%d %H:%M KST')


def dashboard(state, now):
    success = state.get('success')
    content = '<h3>자동 운영 점검 · 참고용</h3>'
    content += '<p>현재 상태는 기존 서비스·서버 화면에서 확인하세요. 이 요약은 자동 복구나 장애 판정이 아닙니다.</p>'
    attempt = state.get('attempt_status', '아직 실행하지 않음')
    if success and attempt != '성공':
        content += '<p><b>주의: ' + html.escape(attempt) + ' · 아래는 이전 분석이며 현재 상태가 아닙니다.</b></p>'
    if not success:
        content += '<p><b>실행 보류 · 성공한 분석 없음</b></p>'
    else:
        content += '<p>분석 구간: ' + stamp(success['time'] - 1800) + ' ~ ' + stamp(success['time'])
        content += '<br>참고 기한: ' + stamp(success['time'] + 3600) + ' 이후 재확인 필요</p>'
        if attempt != '성공':
            content += '<details><summary>이전 분석 보기 · 현재 상태 아님</summary>'
        for env, status in success['statuses'].items():
            content += '<p><b>' + env + '</b>: ' + html.escape(status) + '</p>'
        facts = {f['id']: f for f in success['facts']}
        findings = success['summary']['findings']
        content += '<p>점검 항목 ' + str(len(findings)) + '개 · 우선순위 순. 원인 후보는 확정 진단이 아닙니다.</p>'
        for number, finding in enumerate(findings):
            if number == 5:
                content += '<details><summary>나머지 점검 항목 ' + str(len(findings)-5) + '개 보기</summary>'
            content += '<h4>' + html.escape(LABEL[finding['severity']] + ' · ' + finding['title']) + '</h4><p>' + html.escape(finding['observation']) + '</p><p>'
            content += html.escape(finding['hypothesis']) + '</p><p><b>대응 제안:</b> ' + html.escape(finding['recommendation']) + '</p>'
            content += '<details><summary>확인 순서 · 근거</summary><ol>'
            for check in finding['checks']:
                content += '<li>' + html.escape(check) + '</li>'
            content += '</ol><p><a href="' + html.escape(finding['url'], quote=True) + '">관련 화면 열기</a></p><pre>'
            for ref in finding['evidence']:
                fact = facts[ref]
                content += html.escape(f"{fact['env'] or '전체 서버'} {fact['service'] or ''} {fact['metric']}: {fact['value']}\n")
            content += '</pre></details>'
        if len(findings) > 5:
            content += '</details>'
        if not findings:
            content += '<p>현재 규칙에 해당하는 추가 점검 항목이 없습니다. 정상 판정을 의미하지 않습니다.</p>'
        if attempt != '성공':
            content += '</details>'
    content += '<p>외부 AI/API를 호출하지 않는 규칙 기반 점검입니다. 자원 값은 현재, HTTP 값은 최근 30분 기준입니다.</p>'
    content += '<p>최근 시도: ' + html.escape(state.get('attempt_status', '아직 실행하지 않음')) + '</p>'
    if state.get('attempt_time'):
        content += '<p>최근 시도 시각: ' + stamp(state['attempt_time']) + '</p>'
    panels = [{'id': 4, 'type': 'logs', 'title': '최신 자동 점검 · 행을 펼치면 원인 후보·확인 순서·대응',
               'gridPos': {'x': 0, 'y': 5, 'w': 24, 'h': 9}, 'timeFrom': '24h',
               'datasource': {'type': 'loki', 'uid': 'loki'},
               'targets': [{'refId': 'A', 'expr': '{job="kidly-operations-summary"} | json | line_format "{{.headline}}"', 'queryType': 'range', 'maxLines': 1}],
               'options': {'showTime': True, 'showLabels': False, 'showCommonLabels': False, 'wrapLogMessage': True, 'enableLogDetails': True, 'sortOrder': 'Descending', 'dedupStrategy': 'none'}},
              {'id': 5, 'type': 'row', 'title': '형식화 상세 · 새 점검은 페이지를 새로 열어 확인',
               'collapsed': True, 'gridPos': {'x': 0, 'y': 14, 'w': 24, 'h': 1},
               'panels': [{'id': 1, 'type': 'text', 'title': '페이지 로드 시점의 상세 스냅샷 · 최신 결과는 위 행에서 확인',
                           'gridPos': {'x': 0, 'y': 15, 'w': 24, 'h': 17},
                           'options': {'mode': 'html', 'content': content}}]}]
    for pid, title, expr, unit in [
        (2, '마지막 성공 후 경과 · 1시간 이상이면 오래된 결과', 'time() - (max(kidly_ai_summary_success_timestamp_seconds and (time()-timestamp(kidly_ai_summary_success_timestamp_seconds)<60)) > 0)', 's'),
        (3, '최근 시도 성공 여부 · 0이면 실패/실행 전', 'max(kidly_ai_summary_attempt_success and (time()-timestamp(kidly_ai_summary_attempt_success)<60))', 'none')]:
        panels.append({'id': pid, 'type': 'stat', 'title': title,
                       'gridPos': {'x': (pid-2)*12, 'y': 0, 'w': 12, 'h': 5},
                       'datasource': {'type': 'prometheus', 'uid': 'prometheus'},
                       'targets': [{'refId': 'A', 'expr': expr, 'instant': True}],
                       'fieldConfig': {'defaults': {'unit': unit, 'noValue': '분석 전 / 수집 확인 필요',
                           'mappings': [{'type': 'special', 'options': {'match': 'null', 'result': {'text': '분석 전 / 수집 확인 필요', 'color': 'gray'}}}] + ([] if pid == 2 else [{'type': 'value', 'options': {'0': {'text': '실패 / 실행 전', 'color': 'yellow'}, '1': {'text': '최근 시도 성공', 'color': 'green'}}}]),
                           'thresholds': {'mode': 'absolute', 'steps': ([{'color': 'green', 'value': None}, {'color': 'yellow', 'value': 2400}, {'color': 'red', 'value': 3600}] if pid == 2 else [{'color': 'red', 'value': None}, {'color': 'green', 'value': 1}])}}, 'overrides': []},
                       'options': {'reduceOptions': {'calcs': ['lastNotNull'], 'values': False}, 'colorMode': 'background'}})
    return {'uid': 'kidly-ai-summary', 'title': 'Kidly 자동 운영 점검', 'schemaVersion': 39,
            'editable': False, 'refresh': '30s', 'time': {'from': 'now-1h', 'to': 'now'},
            'tags': ['kidly', 'ai-summary'], 'panels': panels,
            'links': [{'title': '서비스 상태 확인', 'type': 'link', 'url': '/d/kidly-http', 'keepTime': True}]}


def post_report(state):
    """One low-cardinality local Loki stream; no app logs or metric text labels."""
    success = state.get('success')
    lines = ['최근 시도: ' + state.get('attempt_status', '없음')]
    if success:
        lines.append('관측 시각: ' + stamp(success['time']) + ' · 1시간 이후 재확인 필요')
        for env, status in success['statuses'].items():
            lines.append(env + ': ' + status)
        findings = success['summary']['findings']
        for finding in findings:
            lines.extend(['', LABEL[finding['severity']] + ' · ' + finding['title'],
                          '관측: ' + finding['observation'], '원인 후보: ' + finding['hypothesis']])
            lines.extend(str(i+1) + '. ' + check for i, check in enumerate(finding['checks']))
            lines.append('대응 제안: ' + finding['recommendation'])
            lines.append('관련 화면: https://grafana.kidly.kro.kr' + finding['url'])
        headline = f"{state['attempt_status']} · 점검 {len(findings)}개 · " + (findings[0]['title'] if findings else '규칙에 해당하는 추가 항목 없음; 정상 보장은 아님')
    else:
        headline = '실패 · 성공한 점검 없음'
    record = {'headline': headline, 'report': '\n'.join(lines)}
    payload = json.dumps({'streams': [{'stream': {'job': 'kidly-operations-summary'},
                         'values': [[str(time.time_ns()), json.dumps(record, ensure_ascii=False)]]}]}, ensure_ascii=False).encode()
    if len(payload) > 160000:
        raise ValueError('report too large')
    req = urllib.request.Request('http://127.0.0.1:31100/loki/api/v1/push', data=payload,
                                 headers={'Content-Type': 'application/json'}, method='POST')
    with urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect).open(req, timeout=10) as response:
        if response.status != 204:
            raise ValueError('local summary storage unavailable')


def publish(root, state, now):
    atomic(root / 'state.json', json.dumps(state, ensure_ascii=False))
    atomic(root / 'dashboards/kidly-ai-summary.json', json.dumps(dashboard(state, now), ensure_ascii=False), 0o644)
    success_time = state.get('success', {}).get('time', 0)
    # Existing medium Alloy textfile collector; summary text never becomes a metric label.
    metrics = (f'kidly_ai_summary_success_timestamp_seconds {success_time}\n'
               f'kidly_ai_summary_attempt_timestamp_seconds {state.get("attempt_time", 0)}\n'
               f'kidly_ai_summary_attempt_success {int(state.get("attempt_status") == "성공")}\n')
    atomic(root.parent / 'operations/ai-summary.prom', metrics, 0o644)


def run(root, live=False, fixture=None):
    root.mkdir(parents=True, exist_ok=True)
    # Grafana UID 472 needs traversal even when the operator has umask 077.
    root.chmod(0o755)
    (root / 'dashboards').mkdir(exist_ok=True)
    (root / 'dashboards').chmod(0o755)
    with (root / 'run.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return 0
        path = root / 'state.json'
        state = json.loads(path.read_text()) if path.exists() else {}
        if state.get('success'):
            previous = state['success']
            previous['statuses'] = assessment(previous['facts'])
            previous['summary'] = explain(previous['facts'], previous['statuses'])
        now = int(time.time())
        if state.get('attempt_status') == '분석/갱신 중':
            state['attempt_status'] = '비정상 종료 · 이전 결과 재확인 필요'
            publish(root, state, now)
        if not live and fixture is None:
            publish(root, state, now)
            return 0
        if fixture is not None:
            # Only offline tests/demos, distinct output directory required by CLI.
            facts = project(fixture)
            state.update(attempt_time=now, attempt_status='합성 데이터 미리보기')
            state['success'] = {'time': now, 'statuses': assessment(facts), 'facts': facts,
                                'summary': explain(facts, assessment(facts))}
            publish(root, state, now)
            return 0
        if now - state.get('attempt_time', 0) < 1800:
            return 0
        state.update(attempt_time=now, attempt_status='분석/갱신 중')
        # Persist attempt before networking: crashes cannot immediately trigger another request.
        publish(root, state, now)
        previous_success = state.get('success')
        try:
            facts = project(collect(os.environ.get('PROMETHEUS_URL', 'http://127.0.0.1:30090'), now))
            statuses = assessment(facts)
            output = explain(facts, statuses)
            state['success'] = {'time': now, 'statuses': statuses, 'facts': facts, 'summary': output}
            state['attempt_status'] = '성공'
            post_report(state)
            publish(root, state, now)
            return 0
        except Exception:
            # Never print raw upstream responses to journals.
            if previous_success is None:
                state.pop('success', None)
            else:
                state['success'] = previous_success
            state['attempt_status'] = '실패 · 이전 성공 결과는 현재 상태가 아님'
            try:
                post_report(state)
            except Exception:
                pass  # Loki outage: live age/result cards expose failure; do not retry indefinitely.
            publish(root, state, now)
            return 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--state-dir', type=Path, required=True)
    parser.add_argument('--live', action='store_true', help='collect localhost metrics and explain locally; no external calls')
    parser.add_argument('--fixture', type=Path)
    args = parser.parse_args()
    if args.fixture and (args.live or '/opt/app/monitoring' in str(args.state_dir.resolve())):
        parser.error('fixture must use a separate local preview directory')
    try:
        return run(args.state_dir, args.live, json.loads(args.fixture.read_text()) if args.fixture else None)
    except Exception:
        print('Monitoring summary unavailable; check configuration and state permissions', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
