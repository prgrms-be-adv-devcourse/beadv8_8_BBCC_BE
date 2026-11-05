"""No network or API key: provider boundary and one-shot failure guarantees."""
from dataclasses import replace
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
import urllib.error
from unittest.mock import MagicMock, patch

sys.path.insert(0, str(Path(__file__).parents[1]/'ai-notes'))
from contracts import AnalysisContext, Evidence
from groq_provider import GroqProvider, ProviderError, MODEL, ENDPOINT, NoRedirect
from groq_once import run_once, read_key, PRINCIPAL
from storage import Store, LokiPublisher, deliver_accepted
from errors import NotesError


def context(now=2000):
    return AnalysisContext(1, 'trial-context', now-1800, now, ('dev',),
                           (Evidence('f0', 'host_memory_available', 8, 'percent', 'global'),),
                           ('Host aggregate only',))


def body(**changes):
    result = {'findings': [{'observation': 'Memory available is 8 percent',
                           'hypothesis': 'Possible memory pressure, not confirmed',
                           'checks': ['Check the affected host'],
                           'recommendation': 'Measure peak before changing capacity', 'evidence_ids': ['f0']}],
              'limitations': ['No raw logs were read']}
    result.update(changes)
    return json.dumps({'model': MODEL, 'choices': [{'finish_reason': 'stop',
                       'message': {'content': json.dumps(result)}}]}).encode()


def transport(raw=None):
    client = MagicMock()
    response = client.open.return_value.__enter__.return_value
    response.status = 200
    response.read.return_value = body() if raw is None else raw
    return client


class GroqProviderTest(unittest.TestCase):
    def test_stdin_key_boundaries_do_not_echo_input(self):
        for raw in (b'private-without-newline', b'x'*257+b'\n', b'\xff\n'):
            output = io.StringIO()
            with self.subTest(size=len(raw)), patch('sys.stdout', output), self.assertRaisesRegex(ProviderError, '^missing_or_invalid_key$'):
                read_key(io.BytesIO(raw))
            self.assertEqual(output.getvalue(), '')
        self.assertEqual(read_key(io.BytesIO(b'test-not-a-real-api-key\n')), 'test-not-a-real-api-key')

    def provider(self, client):
        return GroqProvider('test-not-a-real-api-key', free_tier_confirmed=True,
                            clock=lambda: 2001, transport=client)

    def test_single_fixed_request_and_adapter_attribution(self):
        client = transport()
        report = self.provider(client).analyze(context(), 2300)
        self.assertEqual((report.provider, report.model, report.generated_at), ('Groq', MODEL, 2001))
        request = client.open.call_args.args[0]
        self.assertEqual(request.full_url, ENDPOINT)
        self.assertEqual(request.get_header('User-agent'), 'kidly-monitoring/1.0')
        payload = json.loads(request.data)
        self.assertEqual(payload['response_format']['json_schema']['strict'], True)
        self.assertEqual(payload['max_completion_tokens'], 3000)
        self.assertNotIn('test-not-a-real-api-key', request.data.decode())
        client.open.assert_called_once()

    def test_free_confirmation_and_key_required_before_network(self):
        for key, confirmed in [('test-not-a-real-api-key', False), ('', True), ('key\n'+('a'*30), True)]:
            with self.subTest(confirmed=confirmed), self.assertRaises(ProviderError):
                GroqProvider(key, free_tier_confirmed=confirmed)

    def test_no_prod_or_expired_request(self):
        client = transport()
        for data, deadline in [(replace(context(), environments=('prod',)), 2300), (context(), 2000)]:
            with self.assertRaises(ProviderError):
                self.provider(client).analyze(data, deadline)
        client.open.assert_not_called()

    def test_http_errors_do_not_retry_or_leak_body(self):
        for status, code in [(429, 'rate_limited'), (401, 'authentication_failed'),
                             (403, 'access_denied'), (500, 'provider_unavailable'), (302, 'provider_unavailable')]:
            client = transport()
            client.open.side_effect = urllib.error.HTTPError(ENDPOINT, status, 'secret-upstream', {}, io.BytesIO(b'secret-body'))
            with self.subTest(status=status), self.assertRaisesRegex(ProviderError, '^'+code+'$'):
                self.provider(client).analyze(context(), 2300)
            client.open.assert_called_once()

    def test_invalid_ref_sensitive_text_and_overlong_result_rejected(self):
        valid = json.loads(json.loads(body())['choices'][0]['message']['content'])
        bad_ref = json.loads(json.dumps(valid)); bad_ref['findings'][0]['evidence_ids'] = ['invented']
        sensitive = json.loads(json.dumps(valid)); sensitive['findings'][0]['recommendation'] = 'password=private'
        for raw in (b'not JSON', b'x'*65537, body(**bad_ref), body(**sensitive), body(findings=[])):
            with self.subTest(size=len(raw)), self.assertRaisesRegex(ProviderError, '^invalid_provider_response$'):
                self.provider(transport(raw)).analyze(context(), 2300)

    def test_truncation_refusal_and_wrong_model_rejected(self):
        for field, value in [('model', 'other-model'), ('finish_reason', 'length'), ('refusal', 'no')]:
            envelope = json.loads(body())
            if field == 'model':envelope[field] = value
            elif field == 'finish_reason':envelope['choices'][0][field] = value
            else:envelope['choices'][0]['message'][field] = value
            with self.subTest(field=field), self.assertRaises(ProviderError):
                self.provider(transport(json.dumps(envelope).encode())).analyze(context(), 2300)

    def test_redirect_never_forwards_authorization(self):
        self.assertIsNone(NoRedirect().redirect_request(None, None, 302, '', {}, 'https://other.invalid'))

    def test_provider_failure_keeps_existing_note_and_blocks_rapid_recall(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(temp)
            publisher = MagicMock()
            failed_provider = MagicMock()
            failed_provider.analyze.side_effect = ProviderError('rate_limited')
            factory = MagicMock(return_value=failed_provider)
            with self.assertRaisesRegex(ProviderError, 'rate_limited'):
                run_once('test-not-a-real-api-key', free_tier_confirmed=True, store=store,
                         collector=lambda env, now: context(now), provider_factory=factory,
                         publisher=publisher, clock=lambda: 2000)
            publisher.publish.assert_not_called()
            self.assertEqual(store.db.execute('SELECT count(*) FROM notes').fetchone()[0], 0)
            with self.assertRaisesRegex(ProviderError, 'trial_cooldown'):
                run_once('test-not-a-real-api-key', free_tier_confirmed=True, store=store,
                         collector=lambda env, now: context(now), provider_factory=factory,
                         publisher=publisher, clock=lambda: 2031)
            failed_provider.analyze.assert_called_once()

    def test_existing_publisher_receives_server_worker_report(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(temp)
            publisher = MagicMock(); publisher.publish.return_value = 'receipt'
            result = run_once('test-not-a-real-api-key', free_tier_confirmed=True, store=store,
                              collector=lambda env, now: context(now),
                              provider_factory=lambda *args, **kwargs: GroqProvider(
                                  args[0], free_tier_confirmed=True, clock=lambda: 2003, transport=transport()),
                              publisher=publisher, clock=lambda: 2002)
            publication = publisher.publish.call_args.args[0]
            self.assertEqual((publication.source, publication.principal), ('server-worker', PRINCIPAL))
            self.assertEqual(result['receipt'], 'receipt')

    def test_delivery_failure_preserves_result_and_recovery_never_reinfers(self):
        with tempfile.TemporaryDirectory() as temp:
            store = Store(temp)
            client = transport()
            factory = lambda *args, **kwargs: GroqProvider(
                args[0], free_tier_confirmed=True, clock=lambda: 2003, transport=client)
            with patch('storage.opener', side_effect=TimeoutError), self.assertRaisesRegex(NotesError, 'delivery_unavailable'):
                run_once('test-not-a-real-api-key', free_tier_confirmed=True, store=store,
                         collector=lambda env, now: context(now), provider_factory=factory, clock=lambda: 2002)
            self.assertFalse(store.receipt('trial-context', PRINCIPAL)['delivered'])
            saved = store.accepted_payload('trial-context')[1]
            loki = MagicMock(); loki.open.return_value.__enter__.return_value.status = 204
            with patch('storage.opener', return_value=loki):
                deliver_accepted(store, 'trial-context')
            self.assertEqual(loki.open.call_args.args[0].data.decode(), saved)
            self.assertTrue(store.receipt('trial-context', PRINCIPAL)['delivered'])
            client.open.assert_called_once()


if __name__ == '__main__':
    unittest.main()
