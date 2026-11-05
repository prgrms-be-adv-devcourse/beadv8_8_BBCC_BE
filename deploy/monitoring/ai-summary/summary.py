#!/usr/bin/env python3
"""Opt-in monitoring summary. No SDK, raw logs, Grafana write token or auto-remediation."""
import argparse
import datetime as dt
import fcntl
import html
import json
import math
import os
from pathlib import Path
import re
import sys
import tempfile
import time
import urllib.parse
import urllib.request

SERVICES = ('gateway', 'search', 'recommend', 'member', 'payment', 'product',
            'order', 'community', 'settlement', 'notification')
JOBS = {'kidly-' + name for name in SERVICES}
MATCH = '|'.join(sorted(JOBS))
SELECT = 'job=~"' + MATCH + '",uri!~"/actuator.*"'
QUERIES = {
    'inventory': 'max by (env,app_job) (kidly_app_expected and (time()-timestamp(kidly_app_expected)<60))',
    'environment': 'max by (env) (kidly_environment_enabled and (time()-timestamp(kidly_environment_enabled)<60))',
    'transition': 'max by (env) (kidly_environment_transition_active and (time()-timestamp(kidly_environment_transition_active)<60))',
    'up': 'min by (env,job) (up{job=~"' + MATCH + '"} and (time()-timestamp(up{job=~"' + MATCH + '"})<60))',
    'requests_30m': 'sum by (env,job) (increase(http_server_requests_seconds_count{' + SELECT + '}[30m]))',
    'errors_30m': 'sum by (env,job) (increase(http_server_requests_seconds_count{' + SELECT + ',status=~"5.."}[30m]))',
    'duration_sum_30m': 'sum by (env,job) (increase(http_server_requests_seconds_sum{' + SELECT + '}[30m]))',
    'hikari_utilization': 'max by (env,job) (hikaricp_connections_active / hikaricp_connections_max and (time()-timestamp(hikaricp_connections_active)<60))',
    'cpu_used_pct': 'max(100 * (1-avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m]))) and on (instance) (max by (instance) (time()-timestamp(node_cpu_seconds_total{mode="idle"}))<60))',
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


def request(url, body=None, headers=None):
    req = urllib.request.Request(url, data=body, headers=headers or {})
    with urllib.request.build_opener(NoRedirect).open(req, timeout=20) as response:
        raw = response.read(MAX_RESPONSE + 1)
    if len(raw) > MAX_RESPONSE:
        raise ValueError('response too large')
    return json.loads(raw)


def collect(base, now):
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost') or parsed.username or parsed.query or parsed.path not in ('', '/'):
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
    """Only fixed env/job names and finite numbers cross the external API boundary."""
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
            if name in ('memory_available_pct', 'disk_used_pct', 'cpu_used_pct'):
                env, job = None, None
            elif env not in ('dev', 'prod') or (name not in ('environment', 'transition', 'lag') and job not in JOBS):
                continue
            value = float(row['value'][1])
            if not math.isfinite(value) or value < 0:
                continue
            key = (env, job)
            if key in seen:
                raise ValueError('ambiguous aggregate')
            seen.add(key)
            facts.append({'id': 'f' + str(len(facts)), 'metric': name, 'env': env,
                          'service': job, 'value': round(value, 4)})
    return facts


def assessment(facts):
    """Deterministic safety gates; AI cannot turn unknown/stopped into healthy."""
    statuses = {}
    for env in ('dev', 'prod'):
        def values(metric):
            return [f for f in facts if f['env'] == env and f['metric'] == metric]
        enabled = values('environment')
        inventory = values('inventory')
        if not enabled or not inventory:
            statuses[env] = '확인 불가: 운영 대상 정보 없음'
        elif enabled[0]['value'] == 0:
            statuses[env] = '의도적 중지'
        elif any(f['value'] == 1 for f in values('transition')):
            statuses[env] = '중지·재개 작업 중'
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


SCHEMA = {'type': 'object', 'additionalProperties': False, 'required': ['findings'],
          'properties': {'findings': {'type': 'array', 'maxItems': 3, 'items': {
              'type': 'object', 'additionalProperties': False,
              'required': ['observation', 'hypothesis', 'next_check', 'evidence'],
              'properties': {**{key: {'type': 'string', 'maxLength': 240} for key in
                                ('observation', 'hypothesis', 'next_check')},
                             'evidence': {'type': 'array', 'minItems': 1, 'maxItems': 5,
                                          'items': {'type': 'string'}}}}}}}


def validate(output, facts):
    if set(output) != {'findings'} or not isinstance(output['findings'], list) or len(output['findings']) > 3:
        raise ValueError('invalid summary')
    ids = {f['id'] for f in facts}
    for finding in output['findings']:
        if set(finding) != {'observation', 'hypothesis', 'next_check', 'evidence'}:
            raise ValueError('invalid finding')
        for key in ('observation', 'hypothesis', 'next_check'):
            if not isinstance(finding[key], str) or len(finding[key]) > 240:
                raise ValueError('invalid text')
        refs = finding['evidence']
        if not isinstance(refs, list) or not 1 <= len(refs) <= 5 or any(not isinstance(ref, str) or ref not in ids for ref in refs):
            raise ValueError('unknown evidence')
    return output


def analyze(facts, statuses, model, key):
    content = json.dumps({'window_minutes': 30, 'status': statuses, 'facts': facts}, ensure_ascii=False)
    if len(content) > 16000:
        raise ValueError('input too large')
    body = {'model': model, 'store': False, 'max_output_tokens': 1200,
            'instructions': ('한국어 운영 점검 메모. 제공한 사실만 근거로 최대 3개 항목. '
                             '원인은 단정하지 말고 hypothesis에 추정임을 명시. 자동 조치 금지. '
                             '중지는 장애 아님. 누락/저트래픽/관측값 0은 정상 증거 아님. '
                             'Gateway와 서비스 요청 합산 금지. HTTP 지표는 30분 추정 건수이며 '
                             '요청 20건 미만 비율은 판단 보류. duration_sum/requests는 평균이지 p95가 아님. '
                             'lag 합계만으로 증가 추세/장애 단정 금지. 없는 지표·수치·링크 생성 금지. '
                             '각 항목 evidence에 제공된 fact id를 넣고, 문제 근거가 없으면 빈 findings.'),
            'input': content,
            'text': {'format': {'type': 'json_schema', 'name': 'monitoring_summary', 'strict': True, 'schema': SCHEMA}}}
    encoded = json.dumps(body, ensure_ascii=False).encode()
    if len(encoded) > 24000:
        raise ValueError('request byte budget exceeded')
    response = request('https://api.openai.com/v1/responses', encoded,
                       {'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    if response.get('status') != 'completed':
        raise ValueError('incomplete AI response')
    texts = [part['text'] for item in response.get('output', []) if item.get('type') == 'message'
             for part in item.get('content', []) if part.get('type') == 'output_text']
    if len(texts) != 1:
        raise ValueError('missing AI output')
    return validate(json.loads(texts[0]), facts)


def stamp(epoch):
    return dt.datetime.fromtimestamp(epoch, dt.timezone(dt.timedelta(hours=9))).strftime('%m/%d %H:%M KST')


def dashboard(state, now):
    success = state.get('success')
    content = '<h3>AI 운영 요약 · 참고용</h3>'
    content += '<p>현재 상태는 기존 서비스·서버 화면에서 확인하세요. 이 요약은 자동 복구나 장애 판정이 아닙니다.</p>'
    if not success:
        content += '<p><b>실행 보류 · 성공한 분석 없음</b></p>'
    else:
        content += '<p>분석 구간: ' + stamp(success['time'] - 1800) + ' ~ ' + stamp(success['time'])
        content += '<br>참고 기한: ' + stamp(success['time'] + 3600) + ' 이후 재확인 필요</p>'
        for env, status in success['statuses'].items():
            content += '<p><b>' + env + '</b>: ' + html.escape(status) + '</p>'
        facts = {f['id']: f for f in success['facts']}
        for finding in success['summary']['findings']:
            content += '<h4>' + html.escape(finding['observation']) + '</h4><p>'
            content += html.escape(finding['hypothesis']) + '<br>다음 확인: ' + html.escape(finding['next_check']) + '</p>'
            content += '<details><summary>근거 수치</summary><pre>'
            for ref in finding['evidence']:
                fact = facts[ref]
                content += html.escape(f"{fact['env'] or '전체 서버'} {fact['service'] or ''} {fact['metric']}: {fact['value']}\n")
            content += '</pre></details>'
        if not success['summary']['findings']:
            content += '<p>AI가 추가 점검 항목을 작성하지 않았습니다. 정상 판정을 의미하지 않습니다.</p>'
    content += '<p>최근 시도: ' + html.escape(state.get('attempt_status', '아직 실행하지 않음')) + '</p>'
    if state.get('attempt_time'):
        content += '<p>최근 시도 시각: ' + stamp(state['attempt_time']) + '</p>'
    panels = [{'id': 1, 'type': 'text', 'title': '최근 분석', 'gridPos': {'x': 0, 'y': 5, 'w': 24, 'h': 17},
               'options': {'mode': 'html', 'content': content}}]
    for pid, title, expr, unit in [
        (2, '마지막 성공 후 경과 · 1시간 이상이면 오래된 결과', 'time() - (max(kidly_ai_summary_success_timestamp_seconds) > 0)', 's'),
        (3, '최근 시도 성공 여부 · 0이면 실패/실행 전', 'max(kidly_ai_summary_attempt_success)', 'none')]:
        panels.append({'id': pid, 'type': 'stat', 'title': title,
                       'gridPos': {'x': (pid-2)*12, 'y': 0, 'w': 12, 'h': 5},
                       'datasource': {'type': 'prometheus', 'uid': 'prometheus'},
                       'targets': [{'refId': 'A', 'expr': expr, 'instant': True}],
                       'fieldConfig': {'defaults': {'unit': unit, 'noValue': '미수집 · 갱신 상태 확인 불가',
                           'thresholds': {'mode': 'absolute', 'steps': ([{'color': 'green', 'value': None}, {'color': 'yellow', 'value': 1800}, {'color': 'red', 'value': 3600}] if pid == 2 else [{'color': 'red', 'value': None}, {'color': 'green', 'value': 1}])}}, 'overrides': []},
                       'options': {'reduceOptions': {'calcs': ['lastNotNull'], 'values': False}, 'colorMode': 'background'}})
    return {'uid': 'kidly-ai-summary', 'title': 'Kidly AI 운영 요약', 'schemaVersion': 39,
            'editable': False, 'refresh': '30s', 'time': {'from': 'now-1h', 'to': 'now'},
            'tags': ['kidly', 'ai-summary'], 'panels': panels,
            'links': [{'title': '서비스 상태 확인', 'type': 'link', 'url': '/d/kidly-http', 'keepTime': True}]}


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
    with (root / 'run.lock').open('w') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return 0
        path = root / 'state.json'
        state = json.loads(path.read_text()) if path.exists() else {}
        now = int(time.time())
        if not live and fixture is None:
            publish(root, state, now)
            return 0
        if fixture is not None:
            # Only offline tests/demos, distinct output directory required by CLI.
            facts = project(fixture)
            state.update(attempt_time=now, attempt_status='합성 데이터 미리보기')
            state['success'] = {'time': now, 'statuses': assessment(facts), 'facts': facts,
                                'summary': {'findings': []}}
            publish(root, state, now)
            return 0
        # Fail closed: no paid request without operator activation and an explicit daily cap/model/key.
        if os.environ.get('OPENAI_LIVE_ENABLED') != '1':
            raise ValueError('live execution disabled')
        cap = int(os.environ.get('OPENAI_MAX_CALLS_PER_DAY', '0'))
        model = os.environ.get('OPENAI_MODEL', '')
        keyfile = os.environ.get('OPENAI_API_KEY_FILE', '')
        if not 1 <= cap <= 48 or not re.fullmatch(r'[a-zA-Z0-9_.-]{1,80}', model) or not keyfile:
            raise ValueError('approved model, cap and key file required')
        day = dt.datetime.fromtimestamp(now, dt.timezone.utc).date().isoformat()
        if state.get('day') != day:
            state.update(day=day, calls=0)
        if now - state.get('attempt_time', 0) < 1800 or state.get('calls', 0) >= cap:
            return 0
        state.update(attempt_time=now, attempt_status='분석/갱신 중')
        # Persist attempt before networking: crashes cannot immediately trigger another request.
        publish(root, state, now)
        try:
            facts = project(collect(os.environ.get('PROMETHEUS_URL', 'http://127.0.0.1:30090'), now))
            statuses = assessment(facts)
            if all(status.startswith('확인 불가') for status in statuses.values()):
                raise ValueError('no fresh inventory')
            state['calls'] += 1
            publish(root, state, now)  # Reserve one call before sending; failures also count.
            key = Path(keyfile).read_text().strip()
            if not key or '\n' in key:
                raise ValueError('invalid credential')
            output = analyze(facts, statuses, model, key)
            state['success'] = {'time': now, 'statuses': statuses, 'facts': facts, 'summary': output}
            state['attempt_status'] = '성공'
            publish(root, state, now)
            return 0
        except Exception:
            # Never print upstream response bodies, credentials or model input/output to journals.
            state['attempt_status'] = '실패 · 이전 성공 결과는 현재 상태가 아님'
            publish(root, state, now)
            return 1


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--state-dir', type=Path, required=True)
    parser.add_argument('--live', action='store_true')
    parser.add_argument('--fixture', type=Path)
    args = parser.parse_args()
    if args.fixture and (args.live or args.state_dir.resolve() == Path('/opt/app/monitoring/ai-summary-state')):
        parser.error('fixture must use a separate local preview directory')
    try:
        return run(args.state_dir, args.live, json.loads(args.fixture.read_text()) if args.fixture else None)
    except Exception:
        print('AI summary unavailable; check configuration and state permissions', file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
