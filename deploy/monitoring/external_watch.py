#!/usr/bin/env python3
"""Bounded external reads and transition-only notifications. No application operations."""
import argparse
from datetime import datetime, timedelta, timezone
import io
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.parse
import urllib.request
import zipfile

from external_probe import observe

KST = timezone(timedelta(hours=9))
ARTIFACT = 'kidly-external-observation-state'
TARGETS = {
    'gateway-route': ('Gateway 응답', 'https://api.kidly.kro.kr/api/v1/_external-observation', 'code', 'NOT_FOUND', 404),
    'product-read': ('상품 조회 경로', 'https://api.kidly.kro.kr/api/v1/products?page=0&size=1', None, None, 200),
    'grafana-health': ('Grafana', 'https://grafana.kidly.kro.kr/api/health', 'database', 'ok', 200),
}


def operating_window(now):
    local = datetime.fromtimestamp(now, KST)
    return (9, 15) <= (local.hour, local.minute) < (17, 55)


def probe_targets(probe=observe, pause=time.sleep):
    rows = {}
    for key, (_, url, field, value, status) in TARGETS.items():
        rows[key] = probe(key, url, field, value, expected_status=status)
    failed = [key for key, row in rows.items() if not row['ok']]
    if failed:
        pause(30)
        for key in failed:
            _, url, field, value, status = TARGETS[key]
            rows[key] = probe(key, url, field, value, expected_status=status)
    return rows


def transitions(rows, previous, now):
    day = datetime.fromtimestamp(now, KST).date().isoformat()
    old = previous.get('targets', {}) if previous.get('date') == day else {}
    events = []
    current = {}
    for key, row in rows.items():
        before = old.get(key, {})
        down = not row['ok']
        # Preserve a known certificate warning during an outage rather than claiming renewal.
        days = row.get('tls_days')
        certificate = days is not None and days <= 14
        if days is None:
            certificate = bool(before.get('certificate'))
        current[key] = {'down': down, 'certificate': certificate}
        if down and not before.get('down'):
            events.append((key, 'down', row))
        elif not down and before.get('down'):
            events.append((key, 'recovered', row))
        if key == 'product-read':
            continue  # Same API host certificate is already covered by gateway-route.
        if days is not None and certificate and not before.get('certificate'):
            events.append((key, 'certificate', row))
        elif days is not None and not certificate and before.get('certificate'):
            events.append((key, 'renewed', row))
    return events, {'date': day, 'observed_at': int(now), 'targets': current}


def message(event, run_url, trial=False):
    key, kind, row = event
    label = TARGETS[key][0]
    prefix = '[시험] ' if trial else ''
    if kind == 'down':
        title = f'{prefix}운영 · 외부 접근 실패 · {label}'
        body = '30초 간격 재확인 후에도 기대 응답을 받지 못했습니다.'
        action = ('DNS·TLS·엣지와 Gateway 상태를 확인하세요. 상품 경로도 실패했는지 비교하세요.'
                  if key == 'gateway-route' else 'Gateway 결과와 검색 서비스·Elasticsearch 상태를 비교하세요.'
                  if key == 'product-read' else '모니터링 서버·엣지·Grafana 상태를 확인하세요. 내부 알림도 멈췄을 수 있습니다.')
    elif kind == 'recovered':
        title = f'{prefix}운영 · 외부 응답 회복 · {label}'
        body = '이번 조회에서 기대 응답을 받았습니다. 전체 업무 복구를 보장하지 않습니다.'
        action = '이전 실패 시간대와 서버 상태를 함께 확인하세요.'
    elif kind == 'certificate':
        title = f'{prefix}운영 · 인증서 만료 임박 · {label}'
        body = f'검증된 인증서 잔여 {row["tls_days"]}일(14일 이하)입니다.'
        action = '엣지 인증서 자동 갱신·발급 상태를 확인하세요.'
    else:
        title = f'{prefix}운영 · 인증서 잔여일 회복 · {label}'
        body = f'검증된 인증서 잔여 {row["tls_days"]}일입니다.'
        action = '인증서 교체 결과를 확인하세요.'
    return '\n'.join((title, body, f'HTTP {row.get("status")} (기대 {TARGETS[key][4]}) · {row.get("duration_ms")}ms', action,
                      f'실행 기록: {run_url}'))


def send_slack(text):
    webhook = os.environ.get('SLACK_OPS_WEBHOOK_URL', '')
    parsed = urllib.parse.urlsplit(webhook)
    if (parsed.scheme != 'https' or parsed.hostname != 'hooks.slack.com'
            or not parsed.path.startswith('/services/') or parsed.username or parsed.password):
        raise RuntimeError('운영 Slack 웹훅 설정 확인 필요')
    request = urllib.request.Request(webhook, data=json.dumps({'text': text}).encode(),
                                     headers={'Content-Type': 'application/json'}, method='POST')
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise RuntimeError('Slack redirect refused')
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=10) as response:
            if response.status != 200 or response.read(16).strip() != b'ok':
                raise RuntimeError('Slack notification not acknowledged')
    except Exception:
        # Never propagate an exception that could contain a webhook URL.
        raise RuntimeError('운영 Slack 전송 실패') from None


def gh_json(endpoint, binary=False):
    try:
        output = subprocess.check_output(['gh', 'api', endpoint], stderr=subprocess.PIPE, timeout=20)
        return output if binary else json.loads(output)
    except (subprocess.SubprocessError, OSError, ValueError):
        raise RuntimeError('이전 외부 점검 상태 조회 실패') from None


def previous_state(repo, now):
    # Only this trusted workflow on develop can supply notification state.
    runs = gh_json(f'repos/{repo}/actions/workflows/external-observation.yml/runs?branch=develop&status=completed&per_page=10')
    for run in runs['workflow_runs']:
        if run['event'] not in ('schedule', 'workflow_dispatch'):
            continue
        artifacts = gh_json(f'repos/{repo}/actions/runs/{run["id"]}/artifacts')['artifacts']
        candidates = [a for a in artifacts if a['name'] == ARTIFACT and not a['expired']]
        if not candidates:
            continue
        raw = gh_json(f'repos/{repo}/actions/artifacts/{candidates[0]["id"]}/zip', binary=True)
        try:
            with zipfile.ZipFile(io.BytesIO(raw)) as archive:
                if archive.getinfo('state.json').file_size > 16384:
                    raise ValueError('oversized state')
                state = json.loads(archive.read('state.json'))
            if not isinstance(state.get('targets'), dict) or type(state.get('observed_at')) is not int:
                raise ValueError('invalid state')
            if not 0 <= now - state['observed_at'] < 86400:
                return {}
            for key, value in state['targets'].items():
                if key not in TARGETS or not isinstance(value, dict):
                    raise ValueError('invalid target')
                if any(type(value.get(field)) is not bool for field in ('down', 'certificate')):
                    raise ValueError('invalid flag')
            return state
        except (ValueError, TypeError, AttributeError, KeyError, zipfile.BadZipFile):
            raise RuntimeError('이전 외부 점검 상태 형식 확인 필요') from None
    return {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--operation', choices=('diagnostic', 'observe-notify', 'notify-test'), default='diagnostic')
    parser.add_argument('--state-file', default='state.json')
    args = parser.parse_args()
    now = time.time()
    repo = os.environ.get('GITHUB_REPOSITORY', '')
    run_id = os.environ.get('GITHUB_RUN_ID', '')
    if args.operation != 'diagnostic' and (not re.fullmatch(r'[\w.-]+/[\w.-]+', repo) or not run_id.isdigit()):
        raise RuntimeError('GitHub 실행 문맥 필요')
    url = f'https://github.com/{repo}/actions/runs/{run_id}'
    if args.operation == 'notify-test':
        fake = {'ok': False, 'status': None, 'tls_days': 60, 'duration_ms': 0}
        send_slack(message(('gateway-route', 'down', fake), url, trial=True))
        fake.update(ok=True, status=404)
        send_slack(message(('gateway-route', 'recovered', fake), url, trial=True))
        print('시험 발화·회복 메시지 Slack 수신 확인(HTTP 응답)')
        return 0
    if args.operation == 'observe-notify' and not operating_window(now):
        print('운영 점검 시간 밖: 외부 조회·Slack 통보 생략')
        return 0
    rows = probe_targets()
    print(json.dumps({'environment': 'dev', 'observations': list(rows.values())}, ensure_ascii=False))
    if args.operation == 'diagnostic':
        return 0 if all(row['ok'] for row in rows.values()) else 1
    previous = previous_state(repo, now)
    events, state = transitions(rows, previous, now)
    # Check the runtime again: late Actions runs must not alert after the planned stop.
    if not operating_window(time.time()):
        print('점검 중 운영시간 종료: 통보·상태 저장 생략')
        return 0
    for event in events:
        send_slack(message(event, url))
    Path(args.state_file).write_text(json.dumps(state))
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write('state_written=true\n')
    print(f'외부 점검 상태 기록: 통보 {len(events)}건')
    return 0 if all(row['ok'] for row in rows.values()) else 1


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(str(error))
        raise SystemExit(2) from None
