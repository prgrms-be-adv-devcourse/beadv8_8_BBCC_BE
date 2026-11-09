"""SQLite context authority and durable local-Loki outbox. No model calls."""
from dataclasses import asdict
import hashlib
import datetime as dt
import os
import tempfile
import json
from pathlib import Path
import sqlite3
import time
import urllib.request

from errors import NotesError
from contracts import AnalysisContext, AnalysisTicket, Evidence
from snapshot import opener


class Store:
    def __init__(self, directory, metrics_path=None):
        directory = Path(directory)
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        directory.chmod(0o700)
        self.metrics_path = Path(metrics_path) if metrics_path else None
        self.db = sqlite3.connect(directory / 'notes.sqlite3', timeout=5)
        (directory/'notes.sqlite3').chmod(0o600)
        self.db.executescript('''
            CREATE TABLE IF NOT EXISTS contexts(id TEXT PRIMARY KEY, body TEXT NOT NULL, started INTEGER NOT NULL, principal TEXT NOT NULL, observed INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS notes(context_id TEXT PRIMARY KEY, digest TEXT NOT NULL, principal TEXT NOT NULL, receipt TEXT NOT NULL, payload TEXT NOT NULL, delivered INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, env TEXT NOT NULL, observed INTEGER NOT NULL, discarded INTEGER NOT NULL DEFAULT 0);
        ''')
        self.db.commit()

    def check_issue_allowed(self, principal, now):
        last = self.db.execute('SELECT max(started) FROM contexts WHERE principal=?', (principal,)).fetchone()[0]
        if last is not None and now-last < 30:
            raise NotesError('rate_limited')

    def issue(self, context, principal, now):
        # Bound storage without deleting valid tickets or pending outbox entries.
        with self.db:
            self.db.execute('DELETE FROM contexts WHERE started < ? AND id NOT IN (SELECT context_id FROM notes)', (now-3600,))
            self.db.execute('DELETE FROM contexts WHERE started < ? AND id NOT IN (SELECT context_id FROM notes WHERE delivered=0 AND discarded=0)', (now-14*86400,))
            self.db.execute('DELETE FROM notes WHERE created < ? AND (delivered=1 OR discarded=1)', (now-14*86400,))
            if self.db.execute('SELECT count(*) FROM contexts').fetchone()[0] >= 2000:
                raise NotesError('capacity_reached')
            if self.db.execute('SELECT count(*) FROM contexts WHERE principal=?', (principal,)).fetchone()[0] >= 1000:
                raise NotesError('capacity_reached')
            last = self.db.execute('SELECT max(started) FROM contexts WHERE principal=?', (principal,)).fetchone()[0]
            if last is not None and now-last < 30:
                raise NotesError('rate_limited')
            self.db.execute('INSERT INTO contexts VALUES(?,?,?,?,?)', (context.context_id, json.dumps(asdict(context)), now, principal, context.window_end))
        self.write_metrics()

    def get(self, context_id):
        row = self.db.execute('SELECT body,started,principal FROM contexts WHERE id=?', (context_id,)).fetchone()
        if row is None:
            raise KeyError('unknown context')
        data = json.loads(row[0])
        context = AnalysisContext(data['version'], data['context_id'], data['window_start'], data['window_end'], tuple(data['environments']), tuple(Evidence(**f) for f in data['evidence']), tuple(data['limitations']))
        return AnalysisTicket(context, row[1], row[2])

    def receipt(self, context_id, principal):
        row = self.db.execute('SELECT receipt,principal,delivered,discarded FROM notes WHERE context_id=?', (context_id,)).fetchone()
        if row is None or row[1] != principal:
            raise KeyError('no receipt for caller')
        return {'receipt': row[0], 'delivered': bool(row[2]), 'discarded': bool(row[3])}

    def prepare(self, publication, payload):
        receipt = hashlib.sha256((publication.context.context_id+publication.content_digest).encode()).hexdigest()[:32]
        with self.db:
            self.db.execute('INSERT OR IGNORE INTO notes VALUES(?,?,?,?,?,0,?,?,?,0)',
                            (publication.context.context_id, publication.content_digest, publication.principal, receipt, payload, publication.published_at, publication.context.environments[0], publication.context.window_end))
            row = self.db.execute('SELECT digest,principal,receipt,payload,delivered,discarded FROM notes WHERE context_id=?', (publication.context.context_id,)).fetchone()
            if row[0] != publication.content_digest or row[1] != publication.principal or row[5]:
                raise NotesError('conflict')
        self.write_metrics()
        return row[2], row[3], bool(row[4])

    def delivered(self, context_id):
        with self.db:
            self.db.execute('UPDATE notes SET delivered=1 WHERE context_id=?', (context_id,))
        self.write_metrics()

    def pending(self):
        rows = self.db.execute('SELECT context_id,env,principal,created FROM notes WHERE delivered=0 AND discarded=0 ORDER BY created LIMIT 2000').fetchall()
        return [dict(zip(('context_id','environment','principal','created'), row)) for row in rows]

    def accepted_payload(self, context_id):
        row = self.db.execute('SELECT receipt,payload,delivered,discarded FROM notes WHERE context_id=?', (context_id,)).fetchone()
        if row is None or row[3]:
            raise KeyError('no accepted outbox')
        return row[0], row[1], bool(row[2])

    def discard(self, context_id):
        with self.db:
            result = self.db.execute('UPDATE notes SET discarded=1 WHERE context_id=? AND delivered=0 AND discarded=0', (context_id,))
            if result.rowcount != 1:
                raise ValueError('only pending notes may be discarded')
        self.write_metrics()

    def write_metrics(self):
        if self.metrics_path is None:
            return
        observed = dict(self.db.execute('SELECT env,MAX(observed) FROM notes WHERE delivered=1 AND discarded=0 GROUP BY env'))
        pending = dict(self.db.execute('SELECT env,count(*) FROM notes WHERE delivered=0 AND discarded=0 GROUP BY env'))
        lines = []
        for env in ('dev', 'prod'):
            lines.append(f'kidly_ai_notes_last_observed_seconds{{env="{env}"}} {observed.get(env, 0)}')
            lines.append(f'kidly_ai_notes_pending{{env="{env}"}} {pending.get(env, 0)}')
        lines.append(f'kidly_ai_notes_contexts {self.db.execute("SELECT count(*) FROM contexts").fetchone()[0]}')
        fd, name = tempfile.mkstemp(dir=self.metrics_path.parent)
        try:
            with os.fdopen(fd, 'w') as output:
                os.fchmod(output.fileno(), 0o644)
                output.write('\n'.join(lines)+'\n')
                output.flush()
                os.fsync(output.fileno())
            os.replace(name, self.metrics_path)
        finally:
            if os.path.exists(name):
                os.unlink(name)


HOST_PERCENT_LABELS = {
    'host_cpu_used': '수집된 호스트 중 최대 CPU 사용률 (5분 평균)',
    'host_memory_available': '수집된 호스트 중 최소 가용 메모리 비율',
    'host_disk_used': '수집된 호스트 중 최대 루트 디스크 사용률',
}


def render(publication):
    context, report = publication.context, publication.report
    def stamp(epoch):
        return dt.datetime.fromtimestamp(epoch, dt.timezone(dt.timedelta(hours=9))).isoformat(timespec='seconds')
    lines = [f'AI analysis (not confirmed cause) · observed {stamp(context.window_end)}',
             f'Published {stamp(publication.published_at)} · {publication.source}/{publication.principal}',
             f'Self-reported AI: {report.provider}/{report.model}',
             'Stale at publish: '+str(publication.stale_at_publish)]
    facts = {f.evidence_id: f for f in context.evidence}
    for finding in report.findings:
        lines.append('')
        lines.extend(f'{i+1}. {check}' for i, check in enumerate(finding.checks))
        lines.extend(('관측: '+finding.observation, '원인 후보: '+finding.hypothesis))
        lines.append('대응 제안: '+finding.recommendation)
        for ref in finding.evidence_ids:
            fact = facts[ref]
            label = HOST_PERCENT_LABELS.get(fact.metric) if fact.environment == 'global' and fact.unit == 'percent' else None
            if label:
                lines.append(f'근거 {ref}: {label} {fact.value:.1f}%')
            else:
                lines.append(f'근거 {ref}: {fact.environment}/{fact.service or "scope"} {fact.metric}={fact.value} {fact.unit}')
    lines.append('\n제약:')
    lines.extend(dict.fromkeys(context.limitations+report.limitations))
    return {'headline': f'AI 분석 · 관측 {stamp(context.window_end)} · {len(report.findings)}개 항목 · {report.provider}/{report.model}',
            'level': 'info', 'report': '\n'.join(lines), 'context_id': context.context_id, 'stale_at_publish': publication.stale_at_publish}


class LokiPublisher:
    def __init__(self, store):
        self.store = store

    def publish(self, publication):
        record = render(publication)
        # Stable timestamp/line reused on retry; Loki deduplicates identical entries.
        timestamp = str(publication.published_at * 1000000000)
        payload = json.dumps({'streams': [{'stream': {'job': 'kidly-ai-notes', 'env': publication.context.environments[0]},
                                          'values': [[timestamp, json.dumps(record, ensure_ascii=False)]]}]}, ensure_ascii=False)
        if len(payload.encode()) > 100000:
            raise ValueError('note too large')
        receipt, saved, done = self.store.prepare(publication, payload)
        if done:
            return receipt
        return deliver_accepted(self.store, publication.context.context_id)


def deliver_accepted(store, context_id):
    """Operator recovery only after expiry. No caller-supplied text or AI reinference."""
    receipt, saved, done = store.accepted_payload(context_id)
    if done:
        return receipt
    request = urllib.request.Request('http://127.0.0.1:31100/loki/api/v1/push', data=saved.encode(), headers={'Content-Type': 'application/json'}, method='POST')
    try:
        with opener().open(request, timeout=5) as response:
            if response.status != 204:
                raise NotesError('delivery_unavailable')
    except NotesError:
        raise
    except Exception:
        raise NotesError('delivery_unavailable') from None
    store.delivered(context_id)
    return receipt
