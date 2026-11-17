"""Same-origin Grafana action endpoint for an explicitly enabled local trial."""
import argparse
from functools import partial
from http.cookies import CookieError, SimpleCookie
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from pathlib import Path
import re
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener

from analysis_requests import AnalysisRequests, next_trial_at, run_groq_trial, run_trial
from groq_provider import NoRedirect
from receive import STATE

ACTION_PATH = '/kidly-ai-analysis/run'
STATUS_PATH = '/kidly-ai-analysis/status'
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


class AnalysisAPI:
    def __init__(self, authorization, requests, *, origin):
        self.authorization = authorization
        self.requests = requests
        self.origin = origin

    def dispatch(self, method, path, headers):
        if (method, path) not in (('POST', ACTION_PATH), ('GET', STATUS_PATH)):
            return 404, {'state': 'not_found'}
        if method == 'POST':
            if headers.get('Origin') != self.origin:
                return 403, {'state': 'forbidden'}
            if headers.get('Content-Length') not in (None, '0') or headers.get('Transfer-Encoding'):
                return 400, {'state': 'invalid_request'}
        try:
            operator = self.authorization.is_operator(headers.get('Cookie', ''))
        except RuntimeError:
            return 503, {'state': 'unavailable'}
        if method == 'POST':
            return self.requests.request(
                is_operator=operator, provider=headers.get('X-Kidly-AI-Provider', 'groq'),
                paid_confirmed=headers.get('X-Kidly-AI-Confirm-Paid') == 'true',
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
        code, result = self.server.analysis_api.dispatch(self.command, self.path, self.headers)
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--grafana-url', required=True, help='Trusted internal Grafana URL')
    parser.add_argument('--origin', required=True, help='Exact browser Grafana origin')
    parser.add_argument('--key-file', type=Path, required=True)
    parser.add_argument('--openai-key-file', type=Path)
    parser.add_argument('--allow-paid-openai', action='store_true')
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
    )
    with HTTPServer(('127.0.0.1', args.port), AnalysisHTTPHandler) as server:
        server.analysis_api = AnalysisAPI(GrafanaAuthorization(args.grafana_url), requests, origin=args.origin)
        server.serve_forever()


if __name__ == '__main__':
    main()
