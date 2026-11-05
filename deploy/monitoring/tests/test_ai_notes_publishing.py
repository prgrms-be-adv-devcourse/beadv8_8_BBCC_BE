import base64
from dataclasses import asdict, replace
import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

SOURCE = Path(__file__).parents[1]/'ai-notes'
sys.path.insert(0, str(SOURCE))
import contracts as c
import mcp_server as mcp
import receive
import snapshot
from storage import Store, LokiPublisher


def context(now=2000):
    return c.AnalysisContext(1, 'test-context', now-1800, now, ('dev',),
                             (c.Evidence('f0', 'host_memory_available', 8, 'percent', 'global'),),
                             ('Only aggregate host evidence',))


def report():
    return c.AnalysisReport(1, 'test-context', 'test-client', 'test-model', 2001,
                            (c.Finding('Memory low', 'Possible pressure, unconfirmed', ('Check host memory',),
                                       'Review container load before restarting', ('f0',)),), ('No raw logs were read',))


def response(status=204, raw=b'{}'):
    fake = MagicMock()
    fake.__enter__.return_value.status = status
    fake.__enter__.return_value.read.return_value = raw
    return fake


class NotesPublishingTest(unittest.TestCase):
    def test_issue_infer_publish_and_receipt_after_deadline(self):
        with tempfile.TemporaryDirectory() as temp:
            metrics = Path(temp)/'metrics.prom'
            store = Store(Path(temp)/'state', metrics)
            issued = receive.handle({'action': 'get_context', 'environment': 'dev'}, 'actor', store, lambda: 2000, lambda env, now: context(now))
            self.assertEqual(issued['deadline'], 2300)
            opener = MagicMock()
            opener.open.return_value = response()
            request = {'action': 'publish', 'context_id': 'test-context', 'report': json.loads(json.dumps(asdict(report())))}
            with patch('storage.opener', return_value=opener):
                first = receive.handle(request, 'actor', store, lambda: 2002)
                second = receive.handle(request, 'actor', store, lambda: 2003)
            self.assertEqual(first['receipt'], second['receipt'])
            opener.open.assert_called_once()
            receipt = receive.handle({'action': 'receipt', 'context_id': 'test-context'}, 'actor', store, lambda: 5000)
            self.assertTrue(receipt['delivered'])
            self.assertIn('kidly_ai_notes_last_observed_seconds{env="dev"} 2000', metrics.read_text())
            self.assertIn('kidly_ai_notes_pending{env="dev"} 0', metrics.read_text())

    def test_failed_delivery_preserves_exact_outbox_for_retry(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(Path(temp)/'state', Path(temp)/'metrics.prom')
            store.issue(context(), 'actor', 2000)
            publisher = LokiPublisher(store)
            with patch('storage.opener', side_effect=TimeoutError):
                with self.assertRaises(TimeoutError):
                    c.submit_analysis('test-context', report(), store, publisher, principal='actor', clock=lambda: 2002)
            self.assertFalse(store.receipt('test-context', 'actor')['delivered'])
            saved = store.db.execute('SELECT payload FROM notes').fetchone()[0]
            opener = MagicMock()
            opener.open.return_value = response()
            with patch('storage.opener', return_value=opener):
                c.submit_analysis('test-context', report(), store, publisher, principal='actor', clock=lambda: 2003)
            self.assertEqual(opener.open.call_args.args[0].data.decode(), saved)
            self.assertEqual(opener.open.call_args.args[0].full_url, 'http://127.0.0.1:31100/loki/api/v1/push')

    def test_different_report_conflicts_and_different_caller_denied(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(temp)
            store.issue(context(), 'actor', 2000)
            opener = MagicMock()
            opener.open.return_value = response()
            with patch('storage.opener', return_value=opener):
                publisher = LokiPublisher(store)
                c.submit_analysis('test-context', report(), store, publisher, principal='actor', clock=lambda: 2002)
                with self.assertRaises(ValueError):
                    c.submit_analysis('test-context', replace(report(), model='another'), store, publisher, principal='actor', clock=lambda: 2003)
                with self.assertRaises(PermissionError):
                    c.submit_analysis('test-context', report(), store, publisher, principal='intruder', clock=lambda: 2003)
                opener.open.assert_called_once()
            with self.assertRaises(KeyError):
                store.receipt('test-context', 'intruder')

    def test_rate_limit_precedes_metric_read_and_model_input_cannot_supply_identity_or_context(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(temp)
            store.issue(context(), 'actor', 2000)
            collector = MagicMock()
            with self.assertRaises(ValueError):
                receive.handle({'action':'get_context','environment':'dev'}, 'actor', store, lambda: 2001, collector)
            collector.assert_not_called()
            for payload in ({'action':'get_context','environment':'dev','principal':'intruder'},
                            {'action':'publish','context_id':'test-context','report':{},'context':{'fake':1}},
                            {'action':'shell','command':'anything'}):
                with self.assertRaises(ValueError):
                    receive.handle(payload, 'actor', store)

    def test_only_fixed_stream_labels_and_plain_text_model_output(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(temp)
            store.issue(context(), 'actor', 2000)
            opener = MagicMock()
            opener.open.return_value = response()
            note = replace(report(), findings=(replace(report().findings[0], hypothesis='<script>untrusted</script>'),))
            with patch('storage.opener', return_value=opener):
                c.submit_analysis('test-context', note, store, LokiPublisher(store), principal='actor', clock=lambda: 2002)
            stream = json.loads(opener.open.call_args.args[0].data)['streams'][0]
            self.assertEqual(stream['stream'], {'job':'kidly-ai-notes','env':'dev'})
            # JSON log text, never HTML sent to a web execution endpoint.
            record = json.loads(stream['values'][0][1])
            self.assertIn('untrusted', record['report'])
            self.assertIn('mcp-client/actor', record['report'])
            self.assertIn('Only aggregate host evidence', record['report'])

    def test_snapshot_drops_original_labels_and_marks_missing_data(self):
        data = {'status':'success','data':{'resultType':'vector','result':[{'metric':{'job':'kidly-search','user_id':'private','instance':'private-host'},'value':[2000,'1']}]}}
        opener = MagicMock()
        opener.open.return_value = response(raw=json.dumps(data).encode())
        with patch('snapshot.opener', return_value=opener):
            ctx = snapshot.collect('dev', 2000)
        serialized = json.dumps(asdict(ctx))
        self.assertNotIn('private', serialized)
        self.assertTrue(ctx.evidence)
        for call in opener.open.call_args_list:
            self.assertTrue(call.args[0].startswith('http://127.0.0.1:30090/api/v1/query?'))
        empty = {'status':'success','data':{'resultType':'vector','result':[]}}
        opener.open.return_value = response(raw=json.dumps(empty).encode())
        with patch('snapshot.opener', return_value=opener):
            ctx = snapshot.collect('dev', 2000)
        c.validate_context(ctx, 2000)
        self.assertEqual(len(ctx.evidence), 0)
        self.assertIn('absence is not healthy zero', ' '.join(ctx.limitations))

    def test_mcp_initialize_list_call_and_error_without_payload_leak(self):
        bridge = MagicMock()
        bridge.call.side_effect = [{'context':{'context_id':'issued'}}, ValueError('secret-upstream-error')]
        requests = [{'jsonrpc':'2.0','id':1,'method':'initialize','params':{}},
                    {'jsonrpc':'2.0','id':2,'method':'tools/list'},
                    {'jsonrpc':'2.0','id':3,'method':'tools/call','params':{'name':'get_analysis_context','arguments':{'environment':'dev'}}},
                    {'jsonrpc':'2.0','id':4,'method':'tools/call','params':{'name':'publish_analysis','arguments':{'context_id':'issued','report':{}}}}]
        output = io.StringIO()
        mcp.serve(bridge, io.StringIO(''.join(json.dumps(r)+'\n' for r in requests)), output)
        replies = [json.loads(line) for line in output.getvalue().splitlines()]
        self.assertEqual(len(replies[1]['result']['tools']), 3)
        self.assertFalse(replies[2]['result']['isError'])
        self.assertTrue(replies[3]['result']['isError'])
        self.assertNotIn('secret-upstream-error', output.getvalue())
        bridge.call.assert_any_call({'action':'get_context','environment':'dev'})

    def test_ssh_transport_uses_fixed_command_pinned_host_and_ephemeral_key(self):
        with tempfile.TemporaryDirectory() as temp:
            hosts = Path(temp)/'known_hosts'
            hosts.write_text('pinned-test-host')
            bridge = mcp.SSHBridge({'host':'example.invalid','user':'deploy','keychain_service':'kr.kidly.ai-notes','keychain_account':'actor','known_hosts_file':str(hosts)})
            private = b'-----BEGIN OPENSSH PRIVATE KEY-----\nsynthetic-not-a-key\n'
            paths=[]
            def run(command, **kwargs):
                if command[0]=='/usr/bin/security':
                    return MagicMock(returncode=0,stdout=base64.b64encode(private))
                self.assertEqual(command[-1],'kidly-ai-notes')
                self.assertIn('StrictHostKeyChecking=yes',command)
                self.assertNotIn('EXTRA_SECRET',kwargs['env'])
                path=Path(command[command.index('-i')+1]);paths.append(path)
                self.assertEqual(path.stat().st_mode & 0o777,0o600)
                self.assertEqual(path.read_bytes(),private)
                self.assertNotIn(private.decode(),str(command))
                return MagicMock(returncode=0,stdout=b'{"ok":true,"result":{"receipt":"id"}}')
            with patch.dict(os.environ,{'EXTRA_SECRET':'private'}),patch('mcp_server.subprocess.run',side_effect=run):
                self.assertEqual(bridge.call({'action':'receipt','context_id':'id'}),{'receipt':'id'})
            self.assertFalse(paths[0].exists())

    def test_large_issued_value_rejected_consistently(self):
        invalid = replace(context(), evidence=(replace(context().evidence[0],value=10**400),))
        with self.assertRaises(ValueError):
            c.validate_context(invalid,2000)


if __name__ == '__main__':
    unittest.main()
