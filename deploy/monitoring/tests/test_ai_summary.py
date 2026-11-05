import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, MagicMock

SPEC = importlib.util.spec_from_file_location('ai_summary', Path(__file__).parents[1] / 'ai-summary/summary.py')
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)


def row(value, **labels):
    return {'metric': labels, 'value': [123, str(value)]}


def fixture():
    return {'environment': [row(1, env='dev'), row(0, env='prod')],
            'inventory': [row(1, env='dev', app_job='kidly-search'), row(0, env='prod', app_job='kidly-search')],
            'up': [row(1, env='dev', job='kidly-search')]}


class SummaryTest(unittest.TestCase):
    def test_projection_never_transmits_raw_labels(self):
        source = fixture()
        source['up'][0]['metric'].update(member_id='private-id', instance='private-host')
        source['up'].append(row(1, env='secret-env', job='kidly-search'))
        source['up'].append(row(1, env='dev', job='arbitrary-service'))
        source['log'] = 'private-log'
        serialized = json.dumps(m.project(source))
        for private in ('private-id', 'private-host', 'secret-env', 'arbitrary-service', 'private-log'):
            self.assertNotIn(private, serialized)

    def test_nonfinite_is_missing_not_zero(self):
        source = fixture()
        source['up'][0]['value'][1] = 'NaN'
        self.assertIn('수집 누락', m.assessment(m.project(source))['dev'])

    def test_ambiguous_aggregates_fail(self):
        source = fixture()
        source['up'].append(source['up'][0])
        with self.assertRaises(ValueError):
            m.project(source)

    def test_stopped_environment_not_outage(self):
        self.assertEqual(m.assessment(m.project(fixture()))['prod'], '의도적 중지')

    def test_up_is_not_service_health_assertion(self):
        self.assertIn('정상 보장은 아님', m.assessment(m.project(fixture()))['dev'])

    def test_missing_inventory_is_unknown(self):
        self.assertIn('확인 불가', m.assessment([])['dev'])

    def test_down_and_missing_are_distinct(self):
        source = fixture()
        source['up'][0]['value'][1] = '0'
        self.assertIn('DOWN', m.assessment(m.project(source))['dev'])
        source['up'] = []
        self.assertIn('수집 누락', m.assessment(m.project(source))['dev'])

    def test_transition_is_not_outage(self):
        source = fixture()
        source['transition'] = [row(1, env='dev')]
        self.assertEqual(m.assessment(m.project(source))['dev'], '중지·재개 작업 중')

    def test_unknown_evidence_rejected(self):
        with self.assertRaises(ValueError):
            m.validate({'findings': [{'observation': 'x', 'hypothesis': '', 'next_check': '', 'evidence': ['missing']}]}, [])

    def test_html_output_escaped(self):
        output = {'findings': [{'observation': '<script>x</script>', 'hypothesis': '', 'next_check': '', 'evidence': ['f0']}]}
        facts = m.project(fixture())
        state = {'success': {'time': 1000, 'statuses': m.assessment(facts), 'facts': facts, 'summary': m.validate(output, facts)}}
        document = json.dumps(m.dashboard(state, 1000))
        self.assertNotIn('<script>', document)
        self.assertIn('&lt;script&gt;', document)

    def test_initialize_never_uses_network_or_discards_success(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(m, 'request') as network:
            root = Path(temp) / 'state'
            m.run(root, fixture=fixture())
            before = json.loads((root/'state.json').read_text())['success']
            m.run(root)
            self.assertEqual(json.loads((root/'state.json').read_text())['success'], before)
            network.assert_not_called()
            self.assertEqual((root/'state.json').stat().st_mode & 0o777, 0o600)
            self.assertEqual((root/'dashboards/kidly-ai-summary.json').stat().st_mode & 0o777, 0o644)

    def test_interrupted_attempt_recovers_without_paid_call(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(m, 'request') as network:
            root = Path(temp)
            m.atomic(root/'state.json', json.dumps({'attempt_status': '분석/갱신 중', 'attempt_time': 1, 'calls': 1}))
            m.run(root)
            state = json.loads((root/'state.json').read_text())
            self.assertIn('비정상 종료', state['attempt_status'])
            self.assertEqual(state['calls'], 1)
            network.assert_not_called()

    def test_unknown_card_gray_and_failed_summary_collapsed(self):
        source = m.dashboard({}, 1000)
        self.assertEqual(source['panels'][1]['fieldConfig']['defaults']['mappings'][0]['options']['result']['color'], 'gray')
        facts = m.project(fixture())
        state = {'attempt_status': '실패', 'success': {'time': 1000, 'statuses': m.assessment(facts), 'facts': facts, 'summary': {'findings': []}}}
        content = m.dashboard(state, 1000)['panels'][0]['options']['content']
        self.assertLess(content.index('주의:'), content.index('운영 대상 수집 중'))
        self.assertIn('<details>', content)

    def test_live_disabled_before_network(self):
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {}, clear=True), patch.object(m, 'request') as network:
            with self.assertRaises(ValueError):
                m.run(Path(temp), live=True)
            network.assert_not_called()

    def test_api_failure_preserves_success_and_reserves_attempt(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / 'state'
            m.run(root, fixture=fixture())
            state = json.loads((root/'state.json').read_text())
            state['attempt_time'] = 0
            m.atomic(root/'state.json', json.dumps(state))
            key = Path(temp)/'key'
            key.write_text('synthetic-not-a-real-key')
            env = {'OPENAI_LIVE_ENABLED': '1', 'OPENAI_MODEL': 'approved-test-model', 'OPENAI_MAX_CALLS_PER_DAY': '1', 'OPENAI_API_KEY_FILE': str(key)}
            with patch.dict(os.environ, env, clear=True), patch.object(m, 'collect', return_value=fixture()), patch.object(m, 'analyze', side_effect=TimeoutError) as api:
                self.assertEqual(m.run(root, live=True), 1)
                self.assertEqual(m.run(root, live=True), 0)
                api.assert_called_once()
            after = json.loads((root/'state.json').read_text())
            self.assertEqual(after['success'], state['success'])
            self.assertEqual(after['calls'], 1)
            self.assertIn('실패', after['attempt_status'])
            self.assertIn('이전 성공 결과', (root/'dashboards/kidly-ai-summary.json').read_text())

    def test_api_uses_fixed_endpoint_and_structured_nonstored_output(self):
        facts = m.project(fixture())
        response = {'status': 'completed', 'output': [{'type': 'message', 'content': [{'type': 'output_text', 'text': '{"findings":[]}'}]}]}
        with patch.object(m, 'request', return_value=response) as req:
            m.analyze(facts, m.assessment(facts), 'test-model', 'synthetic-key')
        args = req.call_args.args
        self.assertEqual(args[0], 'https://api.openai.com/v1/responses')
        body = json.loads(args[1])
        self.assertFalse(body['store'])
        self.assertTrue(body['text']['format']['strict'])
        self.assertNotIn('tools', body)

    def test_refusal_and_incomplete_output_not_published(self):
        for response in ({'status': 'incomplete'}, {'status': 'completed', 'output': []}):
            with self.subTest(response=response), patch.object(m, 'request', return_value=response):
                with self.assertRaises(ValueError):
                    m.analyze([], {}, 'test', 'synthetic-key')

    def test_proxy_environment_is_ignored(self):
        builder = m.urllib.request.build_opener
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'{}'
        def inspect(*args):
            opener = builder(*args)
            self.assertFalse(any(isinstance(h, m.urllib.request.ProxyHandler) for h in opener.handlers))
            fake = MagicMock()
            fake.open.return_value = response
            return fake
        with patch.dict(os.environ, {'http_proxy': 'http://untrusted.invalid:9999', 'https_proxy': 'http://untrusted.invalid:9999'}), patch.object(m.urllib.request, 'build_opener', side_effect=inspect):
            self.assertEqual(m.request('https://api.openai.com/v1/responses'), {})

    def test_remote_prometheus_and_redirects_refused(self):
        with self.assertRaises(ValueError):
            m.collect('http://example.com:30090', 1000)
        with self.assertRaises(ValueError):
            m.NoRedirect().redirect_request(None)


if __name__ == '__main__':
    unittest.main()
