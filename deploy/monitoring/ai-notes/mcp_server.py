#!/usr/bin/env python3
"""Portable stdio MCP adapter. Provider-independent; never calls an AI API."""
import argparse
import base64
import os
import tempfile
import json
from pathlib import Path
import re
import signal
import subprocess
import sys

from errors import CODES, NotesError

PROTOCOL = '2024-11-05'
REPORT_SCHEMA = {
    'type': 'object', 'additionalProperties': False,
    'required': ['version', 'context_id', 'provider', 'model', 'generated_at', 'findings', 'limitations'],
    'properties': {
        'version': {'type': 'integer', 'const': 1}, 'context_id': {'type': 'string', 'maxLength': 100},
        'provider': {'type': 'string', 'minLength': 1, 'maxLength': 100},
        'model': {'type': 'string', 'minLength': 1, 'maxLength': 100},
        'generated_at': {'type': 'integer', 'minimum': 0},
        'limitations': {'type': 'array', 'maxItems': 20, 'items': {'type': 'string', 'minLength': 1, 'maxLength': 1000}},
        'findings': {'type': 'array', 'maxItems': 5, 'items': {
            'type': 'object', 'additionalProperties': False,
            'required': ['observation', 'hypothesis', 'checks', 'recommendation', 'evidence_ids'],
            'properties': {
                **{name: {'type': 'string', 'minLength': 1, 'maxLength': 1000} for name in ('observation', 'hypothesis', 'recommendation')},
                'checks': {'type': 'array', 'minItems': 1, 'maxItems': 5, 'items': {'type': 'string', 'minLength': 1, 'maxLength': 1000}},
                'evidence_ids': {'type': 'array', 'minItems': 1, 'maxItems': 10, 'uniqueItems': True, 'items': {'type': 'string', 'minLength': 1, 'maxLength': 100}},
            }}}
    }}
TOOLS = [
    {'name': 'get_analysis_context', 'description': 'Read an authoritative dev/prod technical metric context for AI inference. Issued evidence expires after 300 seconds. Does not analyze or operate services.',
     'inputSchema': {'type': 'object', 'additionalProperties': False, 'required': ['environment'], 'properties': {'environment': {'type': 'string', 'enum': ['dev', 'prod']}}},
     'annotations': {'readOnlyHint': True, 'destructiveHint': False}},
    {'name': 'publish_analysis', 'description': 'Publish an AI-authored report for an issued context. Does not call an AI or operate services. Do not include URLs, IPs, credentials or personal data. Caller-bound evidence/format/deadline are checked; unknown causes must be hypotheses.',
     'inputSchema': {'type': 'object', 'additionalProperties': False, 'required': ['context_id', 'report'], 'properties': {'context_id': {'type': 'string'}, 'report': REPORT_SCHEMA}},
     'annotations': {'readOnlyHint': False, 'destructiveHint': False, 'idempotentHint': True}},
    {'name': 'get_publication_status', 'description': 'Read the authenticated caller receipt/delivery status. Allowed after context expiry; never republishes or reruns AI inference.',
     'inputSchema': {'type': 'object', 'additionalProperties': False, 'required': ['context_id'], 'properties': {'context_id': {'type': 'string'}}},
     'annotations': {'readOnlyHint': True, 'destructiveHint': False}},
]


class SSHBridge:
    def __init__(self, config):
        if set(config) != {'host', 'user', 'keychain_service', 'keychain_account', 'known_hosts_file'}:
            raise ValueError('invalid bridge config')
        if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9.-]{0,252}', config['host']) or config['user'] != 'deploy':
            raise ValueError('invalid destination')
        if config['keychain_service'] != 'kr.kidly.ai-notes' or not re.fullmatch(r'[a-zA-Z0-9_-]{1,64}', config['keychain_account']):
            raise ValueError('invalid dedicated credential reference')
        path = Path(config['known_hosts_file'])
        if not path.is_absolute() or not path.is_file() or path.is_symlink() or path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o022:
            raise ValueError('absolute installed pinned-host file required')
        self.config = config
        self.command = ['/usr/bin/ssh', '-F', '/dev/null', '-T', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10',
                        '-o', 'StrictHostKeyChecking=yes', '-o', 'IdentitiesOnly=yes', '-o', 'ForwardAgent=no', '-o', 'GlobalKnownHostsFile=/dev/null', '-o', 'IdentityAgent=none', '-o', 'UpdateHostKeys=no', '-o', 'ProxyCommand=none', '-o', 'ProxyJump=none',
                        '-o', 'ClearAllForwardings=yes', '-o', 'PermitLocalCommand=no',
                        '-o', 'UserKnownHostsFile='+str(path)]

    def call(self, payload):
        encoded = json.dumps(payload, ensure_ascii=False).encode()+b'\n'
        if len(encoded) > 50000:
            raise ValueError('request too large')
        # No shell interpolation. An unrestricted key fails the fixed command marker,
        # rather than sending untrusted JSON to an interactive shell.
        env = {key: os.environ[key] for key in ('PATH', 'HOME', 'TMPDIR', 'LANG') if key in os.environ}
        credential = subprocess.run(['/usr/bin/security', 'find-generic-password', '-s', self.config['keychain_service'], '-a', self.config['keychain_account'], '-w'], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=env, timeout=10, check=False)
        if credential.returncode or len(credential.stdout) > 20000:
            raise ValueError('dedicated credential unavailable')
        private = base64.b64decode(credential.stdout.strip(), validate=True)
        if not private.startswith(b'-----BEGIN OPENSSH PRIVATE KEY-----') or len(private) > 16000:
            raise ValueError('invalid dedicated credential')
        with tempfile.TemporaryDirectory(prefix='kidly-notes-', dir='/tmp') as directory:
            identity = Path(directory)/'identity'
            fd = os.open(identity, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'wb') as output:
                output.write(private)
            command = self.command + ['-i', str(identity), self.config['user']+'@'+self.config['host'], 'kidly-ai-notes']
            result = subprocess.run(command, input=encoded, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=env, timeout=40, check=False)
        if len(result.stdout) > 200000:
            raise ValueError('oversized response')
        reply = json.loads(result.stdout)
        if result.returncode or reply.get('ok') is not True:
            error = reply.get('error', {})
            code = error.get('code') if type(error) is dict else 'unavailable'
            raise NotesError(code)
        return reply['result']


def tool_call(bridge, name, arguments):
    fields = {'get_analysis_context': ('get_context', {'environment'}),
              'publish_analysis': ('publish', {'context_id', 'report'}),
              'get_publication_status': ('receipt', {'context_id'})}
    if name not in fields or type(arguments) is not dict or set(arguments) != fields[name][1]:
        raise ValueError('unsupported tool arguments')
    return bridge.call({'action': fields[name][0], **arguments})


def serve(bridge, input_stream, output_stream):
    while True:
        raw = input_stream.readline(70001)
        if not raw:
            return
        if len(raw) > 70000:
            return
        try:
            message = json.loads(raw)
            if type(message) is not dict or message.get('jsonrpc') != '2.0':
                raise ValueError('invalid RPC')
            if 'id' not in message:
                continue
            method = message.get('method')
            if method == 'initialize':
                result = {'protocolVersion': PROTOCOL, 'capabilities': {'tools': {}}, 'serverInfo': {'name': 'kidly-ai-notes', 'version': '1.0.0'}}
            elif method == 'ping':
                result = {}
            elif method == 'tools/list':
                result = {'tools': TOOLS}
            elif method == 'tools/call':
                params = message.get('params', {})
                if type(params) is not dict:
                    raise ValueError('invalid params')
                try:
                    data = tool_call(bridge, params.get('name'), params.get('arguments', {}))
                    result = {'content': [{'type': 'text', 'text': json.dumps(data, ensure_ascii=False)}], 'isError': False}
                except NotesError as error:
                    result = {'content': [{'type': 'text', 'text': 'Operation rejected: '+error.code+'. Check receipt before retrying; expired context requires a new context and analysis.'}], 'isError': True}
                except Exception:
                    result = {'content': [{'type': 'text', 'text': 'Operation unavailable/rejected. Check installed restricted SSH access, issued context/deadline and monitoring health.'}], 'isError': True}
            else:
                output_stream.write(json.dumps({'jsonrpc': '2.0', 'id': message['id'], 'error': {'code': -32601, 'message': 'Method not found'}})+'\n')
                output_stream.flush()
                continue
            output_stream.write(json.dumps({'jsonrpc': '2.0', 'id': message['id'], 'result': result}, ensure_ascii=False)+'\n')
            output_stream.flush()
        except (ValueError, TypeError):
            output_stream.write(json.dumps({'jsonrpc': '2.0', 'id': None, 'error': {'code': -32700, 'message': 'Invalid request'}})+'\n')
            output_stream.flush()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=Path, required=True)
    args = parser.parse_args()
    def stop(*_):
        raise SystemExit(1)
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    signal.signal(signal.SIGHUP, stop)
    serve(SSHBridge(json.loads(args.config.read_text())), sys.stdin, sys.stdout)


if __name__ == '__main__':
    main()
