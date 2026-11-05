#!/usr/bin/env python3
"""Run on the monitoring host. Credentials stay in memory; issue-token is private IPC only."""
import argparse
import base64
import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class ApiError(Exception):
    def __init__(self, status):
        self.status = status
        super().__init__('Grafana API status ' + str(status))


class Grafana:
    def __init__(self):
        command = ['kubectl', '-n', 'monitoring', 'get', 'svc', 'grafana', '-o', 'jsonpath={.spec.clusterIP}']
        self.base = 'http://' + subprocess.check_output(command, text=True).strip() + ':3000'
        password = Path('/opt/app/monitoring/secrets/GRAFANA_ADMIN_PASSWORD').read_text().rstrip('\n')
        self.auth = 'Basic ' + base64.b64encode(('admin:' + password).encode()).decode()
        self.opener = urllib.request.build_opener(NoRedirect())

    def call(self, path, method='GET', data=None, org=None):
        headers = {'Authorization': self.auth, 'Content-Type': 'application/json'}
        if org is not None:
            headers['X-Grafana-Org-Id'] = str(org)
        request = urllib.request.Request(self.base + path, method=method, headers=headers,
                                         data=json.dumps(data).encode() if data is not None else None)
        try:
            with self.opener.open(request, timeout=15) as response:
                payload = response.read()
                return json.loads(payload) if payload else None
        except urllib.error.HTTPError as error:
            status = error.code
            error.close()
            raise ApiError(status) from None


def ensure(api, policy):
    default_org = api.call('/api/user')['orgId']
    try:
        org = api.call('/api/orgs/name/' + urllib.parse.quote(policy['organization'], safe=''))['id']
    except ApiError as error:
        if error.status != 404:
            raise
        org = api.call('/api/orgs', 'POST', {'name': policy['organization']})['orgId']
    sources = api.call('/api/datasources', org=org)
    if any(s['uid'] != policy['datasource_uid'] for s in sources):
        raise ValueError('Dedicated organization contains an unexpected datasource')
    if not sources:
        api.call('/api/datasources', 'POST', {
            'name': policy['datasource_name'], 'uid': policy['datasource_uid'], 'type': 'prometheus',
            'access': 'proxy', 'url': policy['datasource_url'], 'isDefault': True, 'basicAuth': False,
            'jsonData': {'httpMethod': 'POST', 'timeInterval': '30s', 'httpHeaderName1': 'X-Kidly-MCP-Route'},
            'secureJsonData': {'httpHeaderValue1': policy['routing_header']},
        }, org=org)
    source = api.call('/api/datasources/uid/' + policy['datasource_uid'], org=org)
    if source['type'] != 'prometheus' or source['url'] != policy['datasource_url']:
        raise ValueError('Datasource does not use the approved query boundary')
    if source.get('jsonData', {}).get('httpHeaderName1') != 'X-Kidly-MCP-Route':
        raise ValueError('Datasource routing header is absent')
    accounts = api.call('/api/serviceaccounts/search?query=' + urllib.parse.quote(policy['service_account']), org=org)
    accounts = [a for a in accounts['serviceAccounts'] if a['name'] == policy['service_account']]
    if len(accounts) > 1:
        raise ValueError('Duplicate managed service accounts')
    if not accounts:
        account = api.call('/api/serviceaccounts', 'POST',
                           {'name': policy['service_account'], 'role': 'Viewer', 'isDisabled': False}, org=org)
    else:
        account = api.call('/api/serviceaccounts/' + str(accounts[0]['id']), org=org)
    if account['role'] != 'Viewer' or account.get('isDisabled') or account['orgId'] != org:
        raise ValueError('Expected an enabled organization-bound Viewer')
    if api.call('/api/user')['orgId'] != default_org:
        raise ValueError('Admin default organization changed')
    return {'org_id': org, 'account_id': account['id'], 'role': 'Viewer',
            'datasource_uid': policy['datasource_uid'], 'admin_default_org_unchanged': True}


def issue(api, policy, state):
    if sys.stdout.isatty() or os.environ.get('KIDLY_MCP_PRIVATE_CHANNEL') != '1':
        raise ValueError('Use the local installer private IPC channel; never print credentials to a terminal')
    ttl = policy['token_ttl_seconds']
    if not isinstance(ttl, int) or not 0 < ttl <= 604800:
        raise ValueError('Token lifetime must be at most seven days')
    path = '/api/serviceaccounts/' + str(state['account_id']) + '/tokens'
    token = api.call(path, 'POST', {'name': 'codex-' + uuid.uuid4().hex[:12], 'secondsToLive': ttl}, org=state['org_id'])
    try:
        records = api.call(path, org=state['org_id'])
        record = next(t for t in records if t['id'] == token['id'])
        if not record.get('expiration'):
            raise ValueError('Server returned a token without expiration')
        expires = datetime.datetime.fromisoformat(record['expiration'].replace('Z', '+00:00')).timestamp()
        if not 0 < expires - time.time() <= 604860:
            raise ValueError('Unexpected token expiration')
        return dict(state, token_id=token['id'], expires_at=expires, key=token['key'])
    except Exception:
        api.call(path + '/' + str(token['id']), 'DELETE', org=state['org_id'])
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('ensure', 'issue-token', 'revoke-token'))
    parser.add_argument('--policy', type=Path, default=Path(__file__).with_name('policy.json'))
    parser.add_argument('--token-id', type=int)
    args = parser.parse_args()
    policy = json.loads(args.policy.read_text())
    expected = 'http://traefik.kube-system.svc.cluster.local/mcp-prometheus'
    if policy['datasource_url'] != expected or policy['routing_header'] != 'prometheus-readonly-v1':
        raise ValueError('Unexpected datasource security boundary')
    api = Grafana()
    state = ensure(api, policy)
    if args.action == 'issue-token':
        state = issue(api, policy, state)
    elif args.action == 'revoke-token':
        if not args.token_id or args.token_id < 1:
            raise ValueError('Token ID required')
        path = '/api/serviceaccounts/%s/tokens/%s' % (state['account_id'], args.token_id)
        api.call(path, 'DELETE', org=state['org_id'])
        state = {'revoked': True}
    print(json.dumps(state))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # No response bodies, credential objects, request headers or stack traces.
        print('MCP setup failed: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
