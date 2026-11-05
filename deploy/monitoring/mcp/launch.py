#!/usr/bin/env python3
"""PC stdio launcher. No token in configuration, command arguments, or files."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def arguments(binary):
    return [str(binary), '--transport=stdio', '--disable-write', '--enabled-tools=datasource,prometheus',
            '--dynamic-multi-org=false', '--allow-grafana-url-override=false',
            '--allow-cross-origin-redirects=false', '--tls-skip-verify=false', '--debug=false',
            '--grafana-timeout=10s', '--prometheus-guardrail-mode=enforce',
            '--prometheus-guardrail-max-range=1h', '--prometheus-guardrail-max-points=241', '--log-level=error']


def credential(policy, state):
    command = ['/usr/bin/security', 'find-generic-password', '-s', policy['keychain_service'],
               '-a', state['keychain_account'], '-w']
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=15)
    if result.returncode:
        raise RuntimeError('macOS credential unavailable')
    return result.stdout.decode().rstrip('\n')


def environment(policy, state, token):
    # Do not inherit other tokens, Grafana administrator credentials, URL overrides or TLS overrides.
    env = {key: os.environ[key] for key in ('PATH', 'HOME', 'TMPDIR', 'LANG') if key in os.environ}
    env.update(GRAFANA_URL=policy['grafana_url'], GRAFANA_SERVICE_ACCOUNT_TOKEN=token,
               GRAFANA_ORG_ID=str(state['org_id']), GRAFANA_ALLOW_URL_OVERRIDE='false')
    return env


def main():
    root = Path(__file__).resolve().parent
    policy = json.loads((root / 'policy.json').read_text())
    state = json.loads((root / 'connection.json').read_text())
    if state.get('verified') is not True or time.time() >= state['expires_at']:
        raise RuntimeError('Connection unverified or expired; explicit verification/renewal required')
    if policy['grafana_url'] != 'https://grafana.kidly.kro.kr':
        raise RuntimeError('Unexpected Grafana destination')
    binary = root / 'mcp-grafana'
    if hashlib.sha256(binary.read_bytes()).hexdigest() != state['binary_sha256']:
        raise RuntimeError('Installed executable changed')
    token = credential(policy, state)
    os.execve(str(binary), arguments(binary), environment(policy, state, token))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Kidly MCP unavailable: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
