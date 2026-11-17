#!/usr/bin/env python3
"""Operator-only dev trial. Read key from stdin; never persist it or expose in argv."""
import argparse
from contextlib import contextmanager
from functools import partial
from dataclasses import replace
import fcntl
import json
import os
import resource
import signal
import sys
import time

from contracts import analyze_and_publish
from errors import NotesError
from groq_provider import ANALYSIS_FOCUS, GroqProvider, MODEL, ProviderError
from openai_provider import OpenAIProvider
from receive import STATE, METRICS
from snapshot import collect
from storage import LokiPublisher, Store

PRINCIPAL = 'groq-dev-manual'
PAID_PRINCIPAL = 'openai-dev-manual'
TRIAL_INTERVAL = 3600


def last_trial_at(db):
    return db.execute(
        'SELECT max(started) FROM contexts WHERE principal IN (?, ?)', (PRINCIPAL, PAID_PRINCIPAL),
    ).fetchone()[0]


@contextmanager
def trial_lock(directory):
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (directory / 'groq-trial.lock').open('ab') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise ProviderError('trial_running') from None
        yield


def read_key(stream):
    raw = stream.readline(258)
    if len(raw) > 257 or not raw.endswith(b'\n'):
        raise ProviderError('missing_or_invalid_key')
    try:
        return raw[:-1].decode('ascii')
    except UnicodeError:
        raise ProviderError('missing_or_invalid_key') from None


def run_once(key, *, free_tier_confirmed, store, collector=partial(collect, compare_http=True), provider_factory=GroqProvider,
             publisher=None, clock=time.time):
    # Construct/validate credentials and confirmation before collecting or persisting a context.
    provider = provider_factory(key, free_tier_confirmed=free_tier_confirmed, clock=clock)
    return publish_once(provider, store, collector, publisher, clock, PRINCIPAL,
                        provider_name='Groq', model=MODEL)


def publish_once(provider, store, collector, publisher, clock, principal, *, provider_name, model):
    now = int(clock())
    store.check_issue_allowed(principal, now)
    last = last_trial_at(store.db)
    if last is not None and now - last < TRIAL_INTERVAL:
        raise ProviderError('trial_cooldown')
    context = collector('dev', now)
    store.issue(context, principal, now)
    receipt = analyze_and_publish(context, provider, publisher or LokiPublisher(store),
                                  principal=principal, clock=clock)
    return {'context_id': context.context_id, 'receipt': receipt, 'delivered': True,
            'provider': provider_name, 'model': model}


def collect_with_focus(environment, now, *, focus):
    context = collect(environment, now, compare_http=True)
    return replace(context, limitations=context.limitations + ('분석 목적: ' + ANALYSIS_FOCUS[focus][0],))


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--confirm-free-plan', action='store_true')
    mode.add_argument('--confirm-paid-openai', action='store_true')
    parser.add_argument('--focus', choices=ANALYSIS_FOCUS, default='overview')
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
        with trial_lock(STATE):
            store = Store(STATE, METRICS)
            collector = partial(collect_with_focus, focus=args.focus)
            if args.confirm_paid_openai:
                provider = OpenAIProvider(key, paid_confirmed=True, focus=args.focus)
                result = publish_once(provider, store, collector, None,
                                      time.time, PAID_PRINCIPAL, provider_name=provider.name, model=provider.model)
            else:
                result = run_once(key, free_tier_confirmed=args.confirm_free_plan, store=store,
                                  collector=collector, provider_factory=partial(GroqProvider, focus=args.focus))
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
