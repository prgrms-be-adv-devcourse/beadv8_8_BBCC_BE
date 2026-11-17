#!/usr/bin/env python3
"""Send one synthetic span to Alloy or verify its exact ID in Tempo."""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import selectors
import subprocess
import time
from urllib.error import HTTPError
from urllib.request import ProxyHandler, Request, build_opener


class OrderTagMismatch(RuntimeError):
    pass


def fixture(trace_id, now):
    return {'resourceSpans': [{
        'resource': {'attributes': [
            {'key': 'service.name', 'value': {'stringValue': 'kidly-order'}},
            {'key': 'deployment.environment.name', 'value': {'stringValue': 'dev'}},
        ]},
        'scopeSpans': [{
            'scope': {'name': 'kidly.tracing.synthetic'},
            'spans': [{
                'traceId': trace_id, 'spanId': '0000000000000001',
                'name': 'transport fixture', 'kind': 2,
                'startTimeUnixNano': str(now - 1_000_000), 'endTimeUnixNano': str(now),
                'attributes': [
                    {'key': 'http.request.method', 'value': {'stringValue': 'GET'}},
                    {'key': 'http.route', 'value': {'stringValue': '/api/v1/orders/{orderId}'}},
                ],
            }],
        }],
    }]}


@contextmanager
def tunnel(namespace, deployment, port):
    command = ['kubectl', '-n', namespace, 'port-forward', '--address', '127.0.0.1',
               'deployment/' + deployment, f':{port}']
    child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    try:
        with selectors.DefaultSelector() as selected:
            selected.register(child.stdout, selectors.EVENT_READ)
            if not selected.select(timeout=15):
                raise RuntimeError('port forward did not start')
            banner = child.stdout.readline()
        match = re.fullmatch(r'Forwarding from 127\.0\.0\.1:(\d+) -> \d+\s*', banner)
        if not match:
            raise RuntimeError('port forward unavailable')
        yield 'http://127.0.0.1:' + match[1]
    finally:
        if child.poll() is None:
            child.terminate()
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait()
        child.stdout.close()


def send(client, base, trace_id):
    payload = json.dumps(fixture(trace_id, time.time_ns())).encode()
    request = Request(base + '/v1/traces', data=payload, headers={'Content-Type': 'application/json'})
    with client.open(request, timeout=5) as response:
        result = json.loads(response.read(4096))
        if response.status != 200 or result.get('partialSuccess', {}).get('rejectedSpans'):
            raise RuntimeError('Alloy did not accept the fixture')


def verify(client, base, trace_id):
    url = base + '/api/traces/' + trace_id
    request = Request(url, headers={'Accept': 'application/json'})
    for _ in range(20):
        try:
            with client.open(request, timeout=3) as response:
                if response.status == 200:
                    payload = json.load(response)
                    for resource in payload.get('batches', payload.get('resourceSpans', [])):
                        for scope in resource.get('scopeSpans', []):
                            if scope.get('scope', {}).get('name') != 'kidly.tracing.synthetic':
                                continue
                            for span in scope.get('spans', []):
                                if span.get('attributes') == [
                                    {'key': 'kidly.operation', 'value': {'stringValue': 'order.detail'}},
                                ]:
                                    return
                    raise OrderTagMismatch
        except HTTPError as error:
            if error.code != 404:
                raise
        time.sleep(1)
    raise RuntimeError('fixture trace not found in Tempo')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('send', 'verify'))
    parser.add_argument('trace_id')
    args = parser.parse_args()
    if not re.fullmatch(r'[0-9a-f]{32}', args.trace_id) or int(args.trace_id, 16) == 0:
        parser.error('invalid trace id')
    os.environ['KUBECONFIG'] = str(Path.home() / '.kube' / 'config')
    client = build_opener(ProxyHandler({}))
    try:
        if args.mode == 'send':
            with tunnel('kidly-dev', 'tracing-alloy', 4318) as base:
                send(client, base, args.trace_id)
            print('Alloy accepted one synthetic span')
        else:
            with tunnel('monitoring', 'tempo', 3200) as base:
                verify(client, base, args.trace_id)
            print('Tempo returned the tagged synthetic order trace')
    except OrderTagMismatch:
        print('Order fixture did not retain only the expected operation tag')
        return 1
    except (OSError, ValueError, RuntimeError, TypeError, AttributeError):
        print('Could not complete the synthetic ' + args.mode)
        return 1
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
