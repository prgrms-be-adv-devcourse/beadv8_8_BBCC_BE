#!/usr/bin/env python3
"""Operator-only dev trial. Read key from stdin; never persist it or expose in argv."""
import argparse
import json
import os
import resource
import signal
import sys
import time

from contracts import analyze_and_publish
from errors import NotesError
from groq_provider import GroqProvider, MODEL, ProviderError
from receive import STATE, METRICS
from snapshot import collect
from storage import LokiPublisher, Store

PRINCIPAL = 'groq-dev-manual'


def read_key(stream):
    raw = stream.readline(258)
    if len(raw) > 257 or not raw.endswith(b'\n'):
        raise ProviderError('missing_or_invalid_key')
    try:
        return raw[:-1].decode('ascii')
    except UnicodeError:
        raise ProviderError('missing_or_invalid_key') from None


def run_once(key, *, free_tier_confirmed, store, collector=collect, provider_factory=GroqProvider,
             publisher=None, clock=time.time):
    # Construct/validate credentials and confirmation before collecting or persisting a context.
    provider = provider_factory(key, free_tier_confirmed=free_tier_confirmed, clock=clock)
    now = int(clock())
    store.check_issue_allowed(PRINCIPAL, now)
    # At most one trial an hour, including failed inference, across manual workflow invocations.
    last = store.db.execute('SELECT max(started) FROM contexts WHERE principal=?', (PRINCIPAL,)).fetchone()[0]
    if last is not None and now-last < 3600:
        raise ProviderError('trial_cooldown')
    context = collector('dev', now)
    store.issue(context, PRINCIPAL, now)
    receipt = analyze_and_publish(context, provider, publisher or LokiPublisher(store),
                                  principal=PRINCIPAL, clock=clock)
    return {'context_id': context.context_id, 'receipt': receipt, 'delivered': True,
            'provider': 'Groq', 'model': MODEL}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--confirm-free-plan', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    resource.setrlimit(resource.RLIMIT_AS, (384*1024*1024, 384*1024*1024))
    resource.setrlimit(resource.RLIMIT_CPU, (10, 10))
    def timeout(*_):
        raise ProviderError('request_timeout')
    signal.signal(signal.SIGALRM, timeout)
    signal.alarm(100)
    try:
        key = read_key(sys.stdin.buffer)
        result = run_once(key, free_tier_confirmed=args.confirm_free_plan, store=Store(STATE, METRICS))
        print(json.dumps({'ok': True, 'result': result}))
        return 0
    except Exception as error:
        code = str(error) if isinstance(error, ProviderError) else error.code if isinstance(error, NotesError) else 'unavailable'
        print(json.dumps({'ok': False, 'error': code}))
        return 1
    finally:
        signal.alarm(0)


if __name__ == '__main__':
    sys.exit(main())
