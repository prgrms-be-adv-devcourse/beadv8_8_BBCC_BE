"""Request/response contract for an authenticated Grafana analysis action."""
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
from threading import Lock, Thread
import time

from groq_once import PRINCIPAL, TRIAL_INTERVAL


def next_trial_at(directory):
    database = directory / 'notes.sqlite3'
    if not database.exists():
        return 0
    with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True, timeout=2)) as db:
        last = db.execute(
            'SELECT max(started) FROM contexts WHERE principal=?', (PRINCIPAL,),
        ).fetchone()[0]
    return last + TRIAL_INTERVAL if last is not None else 0


def run_groq_trial(key_file, *, free_plan_confirmed):
    """Reuse the same complete path as GitHub Actions, including its shared lock."""
    if not free_plan_confirmed:
        return 'failed'
    with key_file.open('rb') as source:
        raw = source.read(258)
    if not raw or len(raw) > 257:
        return 'failed'
    key = raw.rstrip(b'\n') + b'\n'
    result = subprocess.run(
        [sys.executable, str(Path(__file__).with_name('groq_once.py')), '--confirm-free-plan'],
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
    def __init__(self, runner, next_allowed, *, clock=time.time):
        self.runner = runner
        self.next_allowed = next_allowed
        self.clock = clock
        self.lock = Lock()
        self.status = 'idle'
        self.published_at = None

    def request(self, *, is_operator):
        if not is_operator:
            return 403, {'state': 'forbidden'}
        with self.lock:
            if self.status == 'running':
                return 409, {'state': 'running'}
            try:
                next_at = self.next_allowed()
            except (OSError, sqlite3.Error):
                return 503, {'state': 'unavailable'}
            if next_at > self.clock():
                return 429, {'state': 'limited', 'next_at': next_at}
            self.status = 'running'
            Thread(target=self._run, daemon=True).start()
            return 202, {'state': 'running'}

    def read(self, *, is_operator):
        if not is_operator:
            return 403, {'state': 'forbidden'}
        with self.lock:
            result = {'state': self.status, 'published_at': self.published_at}
            if self.status == 'limited':
                try:
                    result['next_at'] = self.next_allowed()
                except (OSError, sqlite3.Error):
                    return 503, {'state': 'unavailable'}
            return 200, result

    def _run(self):
        try:
            result = self.runner()
        except (OSError, subprocess.SubprocessError, ValueError):
            result = 'failed'
        with self.lock:
            self.status = result
            if result == 'published':
                self.published_at = int(self.clock())
