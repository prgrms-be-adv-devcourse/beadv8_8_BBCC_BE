"""Post the published note's priority lines to Slack after a Grafana/alert-triggered run.

Reads only the note this process just published (same principals, created after the run started).
The webhook URL comes from the server secret file at call time and is never logged or echoed.
"""
from contextlib import closing
import json
import re
import sqlite3
from urllib.request import ProxyHandler, Request, build_opener

from groq_once import PAID_PRINCIPAL, PRINCIPAL
from groq_provider import NoRedirect

WEBHOOK = re.compile(r'https://hooks\.slack\.com/services/[A-Za-z0-9/_-]{1,200}')
DASHBOARD = 'https://grafana.kidly.kro.kr/d/kidly-ai-notes?var-env=dev'
PROVIDERS = {'groq': 'Groq', 'openai': 'OpenAI'}


def latest_priority(directory, since):
    database = directory / 'notes.sqlite3'
    with closing(sqlite3.connect(database.resolve().as_uri() + '?mode=ro', uri=True, timeout=2)) as db:
        row = db.execute('SELECT payload FROM notes WHERE principal IN (?, ?) AND created >= ? AND discarded = 0 '
                         'ORDER BY created DESC LIMIT 1', (PRINCIPAL, PAID_PRINCIPAL, since)).fetchone()
    if row is None:
        return None
    record = json.loads(json.loads(row[0])['streams'][0]['values'][0][1])
    priority = record.get('priority')
    return priority if isinstance(priority, str) and priority else None


def escape(value):
    # Slack mrkdwn control characters only. AI text cannot form links, mentions or channel pings.
    return value.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


def slack_text(priority, *, provider, trigger):
    lines = [f'🤖 AI 운영 분석 · {PROVIDERS.get(provider, provider)} · {escape(trigger)}']
    lines.extend(escape(line) for line in priority.splitlines()[:6])
    lines.append(f'<{DASHBOARD}|전체 메모 보기> · 원인은 가설입니다. 사람이 확인한 뒤 조치하세요')
    return '\n'.join(lines)


class SlackNotifier:
    def __init__(self, state, webhook_file, *, transport=None):
        self.state = state
        self.webhook_file = webhook_file
        self.transport = transport or build_opener(ProxyHandler({}), NoRedirect())

    def __call__(self, *, provider, trigger, since):
        url = self.webhook_file.read_text().strip() if self.webhook_file.exists() else ''
        if not WEBHOOK.fullmatch(url):
            return False
        priority = latest_priority(self.state, since)
        if priority is None:
            return False
        body = json.dumps({'text': slack_text(priority, provider=provider, trigger=trigger)}, ensure_ascii=False).encode()
        request = Request(url, data=body, headers={'Content-Type': 'application/json'}, method='POST')
        with self.transport.open(request, timeout=10) as response:
            return response.status == 200
