#!/usr/bin/env python3
"""One constrained SSH request. No shell, arbitrary URLs, models or service controls."""
import argparse
from dataclasses import asdict
import json
import os
from pathlib import Path
import re
import resource
import signal
import sys
import time

from contracts import report_from_dict, submit_analysis, validate_context
from snapshot import collect
from storage import LokiPublisher, Store

STATE = Path('/opt/app/monitoring/ai-notes-state')
METRICS = Path('/opt/app/monitoring/operations/ai-notes.prom')


def handle(request, principal, store, clock=time.time, collector=collect, publisher=None):
    if type(request) is not dict:
        raise ValueError('object required')
    action = request.get('action')
    if action == 'get_context' and set(request) == {'action', 'environment'}:
        environment = request['environment']
        if environment not in ('dev', 'prod'):
            raise ValueError('unsupported environment')
        now = int(clock())
        # Principal must be fixed by forced command; it is never part of the request.
        store.check_issue_allowed(principal, now)
        context = collector(environment, now)
        validate_context(context, now)
        store.issue(context, principal, now)
        ticket = store.get(context.context_id)
        return {'context': asdict(context), 'deadline': ticket.deadline,
                'instructions': 'Infer using only the issued evidence. Separate facts from hypotheses, give checks/advice, preserve limitations. Submit within deadline; provider/model are self-reported. No automatic remediation.'}
    if action in ('publish', 'receipt'):
        expected = {'action', 'context_id', 'report'} if action == 'publish' else {'action', 'context_id'}
        if set(request) != expected or not isinstance(request['context_id'], str) or not re.fullmatch(r'[a-zA-Z0-9-]{1,100}', request['context_id']):
            raise ValueError('invalid request fields')
        if action == 'receipt':
            return store.receipt(request['context_id'], principal)
        report = report_from_dict(request['report'])
        receipt = submit_analysis(request['context_id'], report, store, publisher or LokiPublisher(store), principal=principal, clock=clock)
        return {'receipt': receipt, 'delivered': True}
    raise ValueError('unsupported operation')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--principal', required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]{1,64}', args.principal):
        return 1
    # With a correctly restricted key, any command other than this marker is refused.
    if os.environ.get('SSH_ORIGINAL_COMMAND') != 'kidly-ai-notes':
        return 1
    os.umask(0o077)
    resource.setrlimit(resource.RLIMIT_AS, (96*1024*1024, 96*1024*1024))
    resource.setrlimit(resource.RLIMIT_CPU, (10, 10))
    signal.alarm(30)
    try:
        raw = sys.stdin.buffer.readline(50001)
        if len(raw) > 50000 or not raw.endswith(b'\n'):
            raise ValueError('request too large or incomplete')
        result = handle(json.loads(raw), args.principal, Store(STATE, METRICS))
        print(json.dumps({'ok': True, 'result': result}, ensure_ascii=False))
        return 0
    except Exception:
        # No raw payloads, credentials, URLs, model output or upstream errors in journals.
        print(json.dumps({'ok': False, 'error': 'Request rejected or local collection/storage unavailable. Check scope, issued context/deadline, receipt and monitoring health.'}))
        return 1


if __name__ == '__main__':
    sys.exit(main())
