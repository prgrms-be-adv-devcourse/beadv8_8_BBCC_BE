#!/usr/bin/env python3
"""Run on the Actions runner; coordinate app lifecycle with independent inventory."""
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import uuid

APP = os.environ['APP_SSH_HOST']
MEDIUM = os.environ['MONITORING_SSH_HOST']
KEY = os.environ.get('DEPLOY_KEY', str(Path.home() / '.ssh/deploy_key'))
for host in (APP, MEDIUM):
    if not re.fullmatch(r'[a-zA-Z0-9.-]+', host):
        raise ValueError('Invalid SSH host')
SCRIPT = '/opt/app/monitoring/environment-state.py'


def remote(host, action, env, data=None):
    if action == 'inventory' and data is not None:
        data = dict(data, actor=os.environ.get('GITHUB_ACTOR', 'local-verification'),
                    run_id=os.environ.get('GITHUB_RUN_ID', 'local-verification'))
    command = shlex.join(['python3', SCRIPT, action, env])
    result = subprocess.run(['ssh', '-i', KEY, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10',
                             'deploy@' + host, command], input=json.dumps(data) if data is not None else None,
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Remote lifecycle helper emits only sanitized errors, never application/compose logs.
        raise RuntimeError('Remote ' + action + ' failed: ' + result.stderr.strip())
    return json.loads(result.stdout) if result.stdout.strip() else None


def install(host):
    subprocess.run(['ssh', '-i', KEY, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', 'deploy@' + host,
                    'mkdir -p /opt/app/monitoring/operations'], check=True)
    temporary = SCRIPT + '.' + uuid.uuid4().hex
    subprocess.run(['scp', '-q', '-i', KEY, '-o', 'BatchMode=yes', 'deploy/environment-state.py',
                    'deploy@' + host + ':' + temporary], check=True)
    subprocess.run(['ssh', '-i', KEY, '-o', 'BatchMode=yes', 'deploy@' + host,
                    shlex.join(['mv', temporary, SCRIPT])], check=True)


def wait_disabled(env, changed_at, timeout=120):
    # Ensure the new intention has reached Prometheus before stopping containers.
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        query = 'max(kidly_environment_enabled{env="' + env + '"} == 0 and on(env) (kidly_environment_changed_timestamp_seconds >= ' + str(changed_at) + '))'
        command = shlex.join(['curl', '-fsS', '--connect-timeout', '3', '--max-time', '5', 'http://127.0.0.1:30090/api/v1/query',
                              '--data-urlencode', 'query=' + query])
        result = subprocess.run(['ssh', '-i', KEY, '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10',
                                 'deploy@' + MEDIUM, command], capture_output=True, text=True, timeout=20)
        if result.returncode == 0:
            values = json.loads(result.stdout).get('data', {}).get('result', [])
            if values and float(values[0]['value'][1]) == 0:
                return
        time.sleep(5)
    raise RuntimeError('Monitoring did not acknowledge stop; containers were not stopped')


def operate(action, env):
    for host in (APP, MEDIUM):
        install(host)
    if action == 'sync':
        data = remote(APP, 'snapshot', env)
        if data:
            remote(MEDIUM, 'inventory', env, data)
        print(env + ': monitoring inventory synchronized' if data else env + ': no deployed inventory')
        return
    old = remote(APP, 'snapshot', env)
    plan = remote(APP, 'plan', env)
    transition = dict(plan, enabled=action == 'resume', phase='starting' if action == 'resume' else 'stopping',
                      changed_at=time.time())
    try:
        remote(MEDIUM, 'inventory', env, transition)
        if action == 'stop':
            wait_disabled(env, transition['changed_at'])
        result = remote(APP, action, env)
    except Exception:
        if action == 'stop':
            # A broken app SSH connection must not prevent restoring medium monitoring.
            remote(MEDIUM, 'inventory', env, old or plan)
        else:
            try:
                observed = remote(APP, 'snapshot', env)
            except Exception:
                # Unknown startup result remains expected and transitioning, never silently muted.
                pass
            else:
                if not observed or not observed['enabled']:
                    remote(MEDIUM, 'inventory', env, old or dict(plan, enabled=False, phase='stopped'))
        raise
    remote(MEDIUM, 'inventory', env, result)
    print(env + ': ' + result['phase'])


if __name__ == '__main__':
    action, env = sys.argv[1:3]
    if action not in ('sync', 'stop', 'resume') or env not in ('dev', 'prod'):
        raise ValueError('Invalid environment operation')
    operate(action, env)
