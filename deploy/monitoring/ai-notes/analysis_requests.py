"""Request/response contract for an authenticated Grafana analysis action."""
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
from threading import Lock, Thread
import time

from groq_provider import ANALYSIS_FOCUS
from groq_once import TRIAL_INTERVAL, last_trial_at


def next_trial_at(directory):
    database = directory / 'notes.sqlite3'
    if not database.exists():
        return 0
    with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True, timeout=2)) as db:
        last = last_trial_at(db)
    return last + TRIAL_INTERVAL if last is not None else 0


def run_groq_trial(key_file, *, free_plan_confirmed, focus="overview"):
    """Reuse the same complete path as GitHub Actions, including its shared lock."""
    if not free_plan_confirmed:
        return 'failed'
    return run_trial(key_file, '--confirm-free-plan', focus=focus)


def run_trial(key_file, confirmation_flag, *, focus="overview"):
    with key_file.open('rb') as source:
        raw = source.read(258)
    if not raw or len(raw) > 257:
        return 'failed'
    key = raw.rstrip(b'\n') + b'\n'
    result = subprocess.run(
        [sys.executable, str(Path(__file__).with_name('groq_once.py')), confirmation_flag, '--focus', focus],
        input=key, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=105,
    )
    response = json.loads(result.stdout)
    if not isinstance(response, dict):
        return 'failed'
    if result.returncode == 0 and response.get('ok') is True:
        return 'published'
    error = response.get('error')
    if error in ('trial_cooldown', 'rate_limited'):
        return 'limited'
    return 'busy' if error == 'trial_running' else 'failed'


class AnalysisRequests:
    def __init__(self, runner, next_allowed, *, openai_runner=None, notifier=None, clock=time.time):
        self.runners = {'groq': runner}
        if openai_runner is not None:
            self.runners['openai'] = openai_runner
        self.next_allowed = next_allowed
        self.clock = clock
        self.lock = Lock()
        self.status = 'idle'
        self.published_at = None
        self.provider = None
        # 게시 뒤 요약을 알릴 곳(Slack). 실패해도 분석 결과에는 영향이 없다
        self.notifier = notifier

    def request(self, *, is_operator, provider='groq', paid_confirmed=False, focus='overview', trigger='Grafana 버튼', share=False):
        if not is_operator:
            return 403, {'state': 'forbidden'}
        if focus not in ANALYSIS_FOCUS:
            return 400, {'state': 'invalid_focus'}
        if provider not in self.runners:
            return 400, {'state': 'provider_unavailable'}
        if provider == 'openai' and paid_confirmed is not True:
            return 400, {'state': 'paid_confirmation_required'}
        with self.lock:
            if self.status == 'running':
                return 409, {'state': 'running'}
            try:
                next_at = self.next_allowed()
            except (OSError, sqlite3.Error):
                return 503, {'state': 'unavailable'}
            if next_at > self.clock():
                self.status = 'limited'
                return 429, {'state': 'limited', 'next_at': next_at}
            self.status = 'running'
            self.provider = provider
            self.thread = Thread(target=self._run, args=(self.runners[provider], focus, provider, trigger, int(self.clock()), share),
                                 daemon=True)
            self.thread.start()
            return 202, {'state': 'running'}

    def read(self, *, is_operator):
        if not is_operator:
            return 403, {'state': 'forbidden'}
        with self.lock:
            result = {'state': self.status, 'published_at': self.published_at,
                      'provider': self.provider, 'providers': list(self.runners)}
            if self.status == 'limited':
                try:
                    result['next_at'] = self.next_allowed()
                except (OSError, sqlite3.Error):
                    return 503, {'state': 'unavailable'}
            return 200, result

    def _run(self, runner, focus, provider='groq', trigger='Grafana 버튼', started=0, share=False):
        try:
            result = runner(focus=focus)
        except Exception:
            # Any runner failure must leave "running"; otherwise every later request stays 409 until restart.
            result = 'failed'
        with self.lock:
            self.status = result
            if result == 'published':
                self.published_at = int(self.clock())
        # 알림이 부른 분석만 Slack 에 올린다(사람이 처리할 알림에 붙는 근거). 버튼 실행은 화면에서만 본다
        if result == 'published' and share and self.notifier is not None:
            try:
                self.notifier(provider=provider, trigger=trigger, since=started)
            except Exception:
                pass
