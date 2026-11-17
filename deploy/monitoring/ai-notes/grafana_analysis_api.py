"""Same-origin Grafana action endpoint for an explicitly enabled local trial."""
import argparse
from functools import partial
import hmac
from http.cookies import CookieError, SimpleCookie
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import os
from pathlib import Path
import re
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener

from analysis_requests import AnalysisRequests, next_trial_at, run_groq_trial, run_trial
from analysis_notify import SlackNotifier
from groq_provider import NoRedirect
from receive import STATE

ACTION_PATH = '/kidly-ai-analysis/run'
STATUS_PATH = '/kidly-ai-analysis/status'
# Grafana 알림(웹훅 연결점 ai-analysis)이 부르는 내부 경로. 엣지 · Ingress 로는 열지 않는다(k8s/ai-analysis.yaml)
ALERT_PATH = '/kidly-ai-analysis/alert'
ALERT_BODY_LIMIT = 65536
# 알림 제목 → 분석 목적. 자동 실행은 항상 무료 제공자(Groq)만 쓴다
ALERT_FOCUS = {'5xx 증가': 'errors', 'Gateway 5xx 증가': 'errors', 'ERROR 로그': 'errors', 'DB 연결 대기 지속': 'latency'}
SESSION_COOKIE = 'grafana_session'
ORG_ID = 1


class GrafanaAuthorization:
    def __init__(self, grafana_url, *, transport=None):
        self.orgs_url = grafana_url.rstrip('/') + '/api/user/orgs'
        self.transport = transport or build_opener(ProxyHandler({}), NoRedirect())

    def is_operator(self, cookie_header):
        if len(cookie_header) > 4096:
            return False
        try:
            cookies = SimpleCookie(cookie_header)
        except CookieError:
            return False
        session = cookies.get(SESSION_COOKIE)
        if session is None or not re.fullmatch(r'[A-Za-z0-9._-]{1,512}', session.value):
            return False
        request = Request(self.orgs_url, headers={
            'Cookie': f'{SESSION_COOKIE}={session.value}',
            'Accept': 'application/json',
        })
        try:
            with self.transport.open(request, timeout=3) as response:
                orgs = json.loads(response.read(16385))
        except HTTPError as error:
            if error.code in (401, 403):
                return False
            raise RuntimeError('grafana_unavailable') from None
        except (OSError, URLError, ValueError):
            raise RuntimeError('grafana_unavailable') from None
        return isinstance(orgs, list) and any(
            isinstance(org, dict) and org.get('orgId') == ORG_ID and org.get('role') == 'Admin'
            for org in orgs
        )


def alert_trigger(payload):
    """Grafana webhook payload → (focus, trigger text). Only labels Grafana sets; no annotations or values."""
    if not isinstance(payload, dict) or payload.get('status') != 'firing':
        return None
    alerts = [item for item in payload.get('alerts') or () if isinstance(item, dict) and item.get('status') == 'firing']
    labels = payload.get('commonLabels') if isinstance(payload.get('commonLabels'), dict) else {}
    name = labels.get('alertname') or (alerts[0].get('labels') or {}).get('alertname') if alerts else labels.get('alertname')
    if not isinstance(name, str) or not 1 <= len(name) <= 50 or any(ord(char) < 32 for char in name):
        return None
    jobs = sorted({(item.get('labels') or {}).get('job') for item in alerts} - {None})
    jobs = [job for job in jobs if isinstance(job, str) and re.fullmatch(r'kidly-[a-z][a-z0-9-]{0,50}', job)][:5]
    trigger = f'알림 "{name}"' + (f' ({", ".join(jobs)})' if jobs else '')
    return ALERT_FOCUS.get(name, 'overview'), trigger


class AnalysisAPI:
    def __init__(self, authorization, requests, *, origin, alert_token_file=None):
        self.authorization = authorization
        self.requests = requests
        self.origin = origin
        self.alert_token_file = alert_token_file

    def alert(self, headers, body):
        try:
            token = self.alert_token_file.read_text().strip() if self.alert_token_file else ''
        except OSError:
            token = ''
        supplied = headers.get('Authorization', '')
        if len(token) < 32 or not hmac.compare_digest(supplied.encode(), ('Bearer ' + token).encode()):
            return 403, {'state': 'forbidden'}
        try:
            trigger = alert_trigger(json.loads(body))
        except ValueError:
            return 400, {'state': 'invalid_request'}
        if trigger is None:
            return 202, {'state': 'ignored'}
        focus, text = trigger
        code, result = self.requests.request(is_operator=True, provider='groq', focus=focus, trigger=text)
        # 한도 · 실행 중도 2xx 로 돌려 Grafana 가 같은 알림을 재전송하지 않게 한다
        return (202 if code in (202, 409, 429) else code), result

    def dispatch(self, method, path, headers, body=b''):
        if (method, path) == ('POST', ALERT_PATH):
            return self.alert(headers, body)
        if (method, path) not in (('POST', ACTION_PATH), ('GET', STATUS_PATH)):
            return 404, {'state': 'not_found'}
        if method == 'POST':
            if headers.get('Origin') != self.origin:
                return 403, {'state': 'forbidden'}
            if (headers.get('Content-Length') not in (None, '0', '2')
                    or headers.get('Transfer-Encoding') or body not in (b'', b'{}')
                    or int(headers.get('Content-Length', '0')) != len(body)):
                return 400, {'state': 'invalid_request'}
        try:
            operator = self.authorization.is_operator(headers.get('Cookie', ''))
        except RuntimeError:
            return 503, {'state': 'unavailable'}
        if method == 'POST':
            return self.requests.request(
                is_operator=operator, provider=headers.get('X-Kidly-AI-Provider', 'groq'),
                paid_confirmed=headers.get('X-Kidly-AI-Confirm-Paid') == 'true',
                focus=headers.get('X-Kidly-AI-Focus', 'overview'),
            )
        return self.requests.read(is_operator=operator)


class AnalysisHTTPHandler(BaseHTTPRequestHandler):
    def setup(self):
        self.request.settimeout(5)
        super().setup()

    def log_message(self, _format, *_args):
        # No access log with session identifiers or request URL parameters.
        pass

    def respond(self):
        body = b''
        if (self.command == 'POST' and self.path == ACTION_PATH and self.headers.get('Content-Length') == '2'
                and not self.headers.get('Transfer-Encoding') and self.headers.get('Origin') == self.server.analysis_api.origin):
            body = self.rfile.read(2)
        elif self.command == 'POST' and self.path == ALERT_PATH and not self.headers.get('Transfer-Encoding'):
            length = self.headers.get('Content-Length', '')
            if not length.isdigit() or int(length) > ALERT_BODY_LIMIT:
                self.send_error(413)
                return
            body = self.rfile.read(int(length))
        code, result = self.server.analysis_api.dispatch(self.command, self.path, self.headers, body)
        body = json.dumps(result, separators=(',', ':')).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    do_GET = respond
    do_POST = respond


STATE_CODES = {'idle': 0, 'running': 1, 'published': 2, 'limited': 3, 'failed': 4, 'busy': 5}


def write_dashboard_status(path, result, now):
    lines = [f'kidly_ai_analysis_heartbeat_seconds {now}',
             f'kidly_ai_analysis_state {STATE_CODES.get(result.get("state"), 4)}']
    for provider in ('groq', 'openai'):
        if provider in result.get('providers', ()):
            lines.append(f'kidly_ai_analysis_provider_enabled{{provider="{provider}"}} 1')
    if result.get('published_at') is not None:
        lines.append(f'kidly_ai_analysis_published_seconds {int(result["published_at"])}')
    with tempfile.NamedTemporaryFile(mode='w', dir=path.parent, delete=False) as target:
        temporary = Path(target.name)
        try:
            target.write('\n'.join(lines) + '\n')
            target.flush()
            os.fchmod(target.fileno(), 0o644)
            temporary.replace(path)
        finally:
            temporary.unlink(missing_ok=True)


class DashboardStatusServer(HTTPServer):
    last_status = 0
    metrics_path = None

    def service_actions(self):
        now = int(time.time())
        if self.metrics_path is not None and now - self.last_status >= 15:
            _, state = self.analysis_api.requests.read(is_operator=True)
            try:
                write_dashboard_status(self.metrics_path, state, now)
            except OSError:
                return
            self.last_status = now


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--grafana-url', required=True, help='Trusted internal Grafana URL')
    parser.add_argument('--origin', required=True, help='Exact browser Grafana origin')
    parser.add_argument('--key-file', type=Path, required=True)
    parser.add_argument('--openai-key-file', type=Path)
    parser.add_argument('--allow-paid-openai', action='store_true')
    parser.add_argument('--metrics-file', type=Path)
    parser.add_argument('--alert-token-file', type=Path, help='Bearer token Grafana alert webhook sends')
    parser.add_argument('--slack-webhook-file', type=Path, help='Slack incoming webhook for run summaries')
    parser.add_argument('--host', default='127.0.0.1', help='Listen address. Server uses the node address the Grafana ingress forwards to')
    parser.add_argument('--port', type=int, default=8786)
    parser.add_argument('--confirm-free-plan', action='store_true', required=True)
    args = parser.parse_args()
    if args.allow_paid_openai != bool(args.openai_key_file):
        parser.error('Paid OpenAI requires both explicit enablement and a server key.')
    openai_runner = (partial(run_trial, args.openai_key_file, '--confirm-paid-openai')
                     if args.allow_paid_openai else None)
    requests = AnalysisRequests(
        partial(run_groq_trial, args.key_file, free_plan_confirmed=args.confirm_free_plan),
        partial(next_trial_at, STATE), openai_runner=openai_runner,
        notifier=SlackNotifier(STATE, args.slack_webhook_file) if args.slack_webhook_file else None,
    )
    with DashboardStatusServer((args.host, args.port), AnalysisHTTPHandler) as server:
        server.metrics_path = args.metrics_file
        server.analysis_api = AnalysisAPI(GrafanaAuthorization(args.grafana_url), requests, origin=args.origin,
                                          alert_token_file=args.alert_token_file)
        server.serve_forever()


if __name__ == '__main__':
    main()
