#!/usr/bin/env python3
"""Receive an approved SSH issuance response through stdin; store the key only in macOS Keychain."""
import argparse
import hmac
import json
import re
import subprocess
import sys
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=Path.home() / '.local/share/kidly-grafana-mcp')
    args = parser.parse_args()
    if sys.stdin.isatty():
        raise ValueError('Use a private pipe; never paste a token in command arguments')
    policy = json.loads((args.directory / 'policy.json').read_text())
    path = args.directory / 'connection.json'
    state = json.loads(path.read_text())
    issued = json.load(sys.stdin)
    token = issued.pop('key')
    if issued.get('role') != 'Viewer' or issued['org_id'] != state['org_id'] or issued['account_id'] != state['account_id']:
        raise ValueError('Unexpected credential identity')
    if not 0 < issued['expires_at'] - time.time() <= 604860:
        raise ValueError('Expected expiration within seven days')
    service, account = policy['keychain_service'], state['keychain_account']
    if not all(re.fullmatch(r'[A-Za-z0-9_.-]+', item) for item in (service, account)):
        raise ValueError('Unexpected Keychain identifier')
    if not re.fullmatch(r'[A-Za-z0-9_=+./-]+', token):
        raise ValueError('Unexpected credential encoding')
    find = ['/usr/bin/security', 'find-generic-password', '-s', service, '-a', account]
    if subprocess.run(find, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        raise ValueError('Existing credential preserved; revoke and renew explicitly')
    command = 'add-generic-password -s ' + service + ' -a ' + account + ' -l "Kidly Grafana MCP Viewer (7 days)" -w ' + token + '\n'
    added = subprocess.run(['/usr/bin/security', '-i'], input=command, text=True, stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, timeout=20)
    if added.returncode:
        raise RuntimeError('Keychain command failed; revoke the just-issued token')
    stored = subprocess.run(find + ['-w'], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, timeout=15)
    if stored.returncode or not hmac.compare_digest(stored.stdout.decode().rstrip('\n'), token):
        raise RuntimeError('Keychain verification failed; revoke the just-issued token')
    state.update(issued)
    state['verified'] = False
    path.write_text(json.dumps(state) + '\n')
    path.chmod(0o600)
    print('Credential stored in Keychain; connection remains unverified')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Credential storage failed: ' + type(error).__name__ + '; revoke the newly issued token', file=sys.stderr)
        sys.exit(1)
