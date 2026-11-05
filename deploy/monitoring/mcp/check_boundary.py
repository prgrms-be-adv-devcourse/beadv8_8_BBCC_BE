#!/usr/bin/env python3
"""Run inside a cluster Pod. Never send valid remote-write payloads or deletion selectors."""
import argparse
import json
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = 'http://traefik.kube-system.svc.cluster.local/mcp-prometheus'
MARKER = 'prometheus-readonly-v1'
VIA_POD = False


def request(method, path, form=None):
    body = urllib.parse.urlencode(form).encode() if form is not None else (b'' if method == 'POST' else None)
    headers = {'X-Kidly-MCP-Route': MARKER, 'Content-Type': 'application/x-www-form-urlencoded'}
    if VIA_POD:
        command = ['kubectl', '-n', 'monitoring', 'exec', 'deployment/grafana', '--',
                   'curl', '--silent', '--show-error', '--max-time', '15', '--include', '--request', method,
                   '--header', 'X-Kidly-MCP-Route: ' + MARKER,
                   '--header', 'Content-Type: application/x-www-form-urlencoded']
        if body is not None:
            command += ['--data-binary', body.decode()]
        raw = subprocess.check_output(command + [BASE + path], stderr=subprocess.DEVNULL)
        head, payload = raw.split(b'\r\n\r\n', 1)
        lines = head.decode('iso-8859-1').splitlines()
        fields = dict(line.split(':', 1) for line in lines[1:] if ':' in line)
        fields = {key.lower(): value.strip() for key, value in fields.items()}
        return int(lines[0].split()[1]), fields.get('x-kidly-mcp-boundary'), payload
    req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        payload = response.read(1048576)
        return response.status, response.headers.get('X-Kidly-MCP-Boundary'), payload


def check_node_origin():
    service = json.loads(subprocess.check_output(
        ['kubectl', '-n', 'kube-system', 'get', 'svc', 'traefik', '-o', 'json'], text=True))
    policy = json.loads(subprocess.check_output(
        ['kubectl', '-n', 'monitoring', 'get', 'middlewares.traefik.io', 'mcp-prometheus-internal', '-o', 'json'], text=True))
    ranges = policy['spec']['ipAllowList']['sourceRange']
    assert len(ranges) == 1 and ranges[0].endswith('/32'), 'Only one Grafana Pod may be allowed'
    url = 'http://' + service['spec']['clusterIP'] + '/mcp-prometheus/api/v1/query?query=vector(1)'
    for spoof in (False, True):
        headers = {'X-Kidly-MCP-Route': MARKER}
        if spoof:
            headers['X-Forwarded-For'] = ranges[0].split('/')[0]
        req = urllib.request.Request(url, headers=headers)
        try:
            response = urllib.request.urlopen(req, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            assert response.status == 403, 'Non-Grafana source must be forbidden'
        print('Node source with forged Grafana IP denied' if spoof else 'Node source denied')


def main():
    now = int(time.time())
    reads = [
        ('instant-get', 'GET', '/api/v1/query?query=vector(1)', None),
        ('instant-post', 'POST', '/api/v1/query', {'query': 'vector(1)'}),
        ('range', 'POST', '/api/v1/query_range', {'query': 'vector(1)', 'start': now - 60, 'end': now, 'step': 30}),
        ('metadata', 'GET', '/api/v1/metadata?metric=up', None),
        ('metric-names', 'GET', '/api/v1/label/__name__/values', None),
        ('label-names', 'GET', '/api/v1/labels', None),
        ('label-values', 'GET', '/api/v1/label/job/values', None),
    ]
    for name, method, path, form in reads:
        status, boundary, body = request(method, path, form)
        assert status == 200 and boundary == 'query-only', (name, status, boundary)
        assert json.loads(body)['status'] == 'success', name
        print(name + ': read succeeded')
    # All probes are harmless even if routing regresses: no valid write payload or deletion selector.
    denied = [
        ('write-post', 'POST', '/api/v1/write'),
        ('write-get', 'GET', '/api/v1/write'),
        ('admin-delete-no-selector', 'POST', '/api/v1/admin/tsdb/delete_series'),
        ('admin-clean-get', 'GET', '/api/v1/admin/tsdb/clean_tombstones'),
        ('admin-snapshot-get', 'GET', '/api/v1/admin/tsdb/snapshot'),
        ('unknown', 'POST', '/api/v1/unknown'),
        ('unknown-root', 'GET', '/unknown'),
        ('query-delete', 'DELETE', '/api/v1/query'),
        ('query-put', 'PUT', '/api/v1/query'),
        ('query-suffix', 'GET', '/api/v1/query/write'),
        ('encoded-slash', 'POST', '/api/v1%2fwrite'),
    ]
    for name, method, path in denied:
        status, boundary, _ = request(method, path)
        assert status == 418 and boundary == 'denied-before-upstream', (name, status, boundary)
        print(name + ': denied by internal noop handler; no Prometheus upstream')

    if VIA_POD:
        check_node_origin()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--via-grafana-pod', action='store_true')
    VIA_POD = parser.parse_args().via_grafana_pod
    main()
