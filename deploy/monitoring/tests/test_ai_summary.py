import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch, MagicMock

SOURCE = Path(__file__).parents[1] / 'ai-summary'
sys.path.insert(0, str(SOURCE))
SPEC = importlib.util.spec_from_file_location('local_summary', SOURCE / 'summary.py')
m = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(m)


def row(value, **labels):
    return {'metric': labels, 'value': [123, str(value)]}


def fixture():
    return {'environment': [row(1, env='dev'), row(0, env='prod')],
            'inventory': [row(1, env='dev', app_job='kidly-search'), row(0, env='prod', app_job='kidly-search')],
            'up': [row(1, env='dev', job='kidly-search')],
            'requests_30m': [row(100, env='dev', job='kidly-search')],
            'errors_30m': [row(0, env='dev', job='kidly-search')],
            'duration_sum_30m': [row(20, env='dev', job='kidly-search')],
            'memory_available_pct': [row(50)], 'cpu_used_pct': [row(20)], 'disk_used_pct': [row(30)],
            'hikari_pending': [row(0, env='dev', job='kidly-search')],
            'hikari_utilization': [row(.1, env='dev', job='kidly-search')], 'lag': [row(0, env='dev')]}


def explain(source):
    facts = m.project(source)
    return m.explain(facts, m.assessment(facts))['findings']


class SummaryTest(unittest.TestCase):
    def test_projection_removes_unknown_labels_and_services(self):
        source = fixture()
        source['up'][0]['metric'].update(member_id='private-id', instance='private-host')
        source['up'].append(row(1, env='unknown-env', job='kidly-search'))
        source['up'].append(row(1, env='dev', job='arbitrary-service'))
        source['log'] = 'private-log'
        serialized = json.dumps(m.project(source))
        for private in ('private-id', 'private-host', 'unknown-env', 'arbitrary-service', 'private-log'):
            self.assertNotIn(private, serialized)

    def test_nonfinite_and_invalid_boolean_are_missing(self):
        for value in ('NaN', 'Inf', '-1', '2'):
            source = fixture()
            source['up'][0]['value'][1] = value
            self.assertIn('수집 누락', m.assessment(m.project(source))['dev'])

    def test_duplicate_aggregate_is_rejected(self):
        source = fixture()
        source['up'].append(source['up'][0])
        with self.assertRaises(ValueError):
            m.project(source)

    def test_stopped_environment_has_no_app_faults(self):
        source = fixture()
        source['environment'][0]['value'][1] = '0'
        source['up'] = []
        source['errors_30m'][0]['value'][1] = '100'
        findings = explain(source)
        self.assertFalse(any(f['code'].startswith(('target-', 'errors-', 'http-coverage-')) for f in findings))
        self.assertTrue(any(f['code'] == 'stopped-dev' for f in findings))

    def test_transition_suppresses_target_fault(self):
        source = fixture()
        source['transition'] = [row(1, env='dev')]
        source['up'] = []
        self.assertFalse(any(f['code'].startswith('target-') for f in explain(source)))
        self.assertTrue(any(f['code'] == 'transition-dev' for f in explain(source)))

    def test_inventory_missing_is_unknown_not_down(self):
        source = fixture()
        source['inventory'] = []
        findings = explain(source)
        self.assertTrue(any(f['code'] == 'inventory-dev' for f in findings))
        self.assertFalse(any(f['severity'] == 'critical' for f in findings))

    def test_down_and_missing_have_actionable_distinct_explanations(self):
        for value, word in ((0, '수집 실패'), (None, '수집 누락')):
            source = fixture()
            source['up'] = [] if value is None else [row(value, env='dev', job='kidly-search')]
            findings = explain(source)
            target = next(f for f in findings if f['code'].startswith('target-'))
            self.assertIn(word, target['title'])
            self.assertTrue(target['checks'])
            self.assertTrue(target['recommendation'])
            self.assertEqual(findings[0]['severity'], 'critical')

    def test_latency_and_db_wait_are_correlated_as_candidates(self):
        source = fixture()
        source['duration_sum_30m'][0]['value'][1] = '200'
        source['hikari_pending'][0]['value'][1] = '3'
        finding = next(f for f in explain(source) if f['code'].startswith('latency-db-'))
        self.assertIn('동반', finding['title'])
        self.assertIn('확정할 수 없습니다', finding['hypothesis'])
        self.assertIn('연결 수부터 늘리지', finding['recommendation'])
        self.assertEqual(len(finding['evidence']), 4)
        self.assertIn('var-env=dev', finding['url'])
        self.assertIn('var-service=kidly-search', finding['url'])

    def test_slow_average_does_not_invent_db_wait_or_p95(self):
        source = fixture()
        source['duration_sum_30m'][0]['value'][1] = '200'
        source['hikari_pending'] = []
        finding = next(f for f in explain(source) if f['code'].startswith('latency-db-'))
        self.assertNotIn('동반', finding['title'])
        self.assertIn('p95 아님', finding['observation'])
        self.assertNotIn('느린 쿼리', finding['hypothesis'])

    def test_low_traffic_and_zero_denominator_do_not_trigger_rate_or_latency(self):
        for count in (0, 1, 19):
            source = fixture()
            source['requests_30m'][0]['value'][1] = str(count)
            source['errors_30m'][0]['value'][1] = '10'
            source['duration_sum_30m'][0]['value'][1] = '500'
            findings = explain(source)
            self.assertFalse(any(f['code'].startswith(('errors-', 'latency-db-')) for f in findings))
            self.assertTrue(any(f['code'] == 'http-coverage-dev' for f in findings))

    def test_missing_http_is_not_healthy_zero(self):
        source = fixture()
        source['errors_30m'] = []
        self.assertTrue(any(f['code'] == 'http-coverage-dev' for f in explain(source)))

    def test_error_count_and_rate_both_required(self):
        source = fixture()
        source['errors_30m'][0]['value'][1] = '10'
        self.assertTrue(any(f['code'].startswith('errors-') for f in explain(source)))
        source['errors_30m'][0]['value'][1] = '4'
        self.assertFalse(any(f['code'].startswith('errors-') for f in explain(source)))

    def test_resource_warnings_do_not_attribute_specific_app_or_server(self):
        source = fixture()
        source['memory_available_pct'][0]['value'][1] = '8'
        finding = next(f for f in explain(source) if f['code'] == 'memory_available_pct')
        self.assertIn('원인 서버는 아직 특정하지', finding['hypothesis'])
        self.assertIn('현재 관측', finding['observation'])

    def test_oom_is_reported_even_after_memory_recovers(self):
        source = fixture()
        source['oom_30m'] = [row(1)]
        finding = next(f for f in explain(source) if f['code'] == 'oom')
        self.assertEqual(finding['severity'], 'critical')
        self.assertIn('현재 여유가 회복돼도', finding['hypothesis'])

    def test_missing_resources_and_kafka_are_unknown(self):
        source = fixture()
        source['memory_available_pct'] = []
        source['lag'] = []
        self.assertTrue(any(f['code'] == 'resource-missing' for f in explain(source)))
        self.assertTrue(any(f['code'] == 'lag-missing-dev' for f in explain(source)))

    def test_lag_does_not_claim_increasing_or_suggest_offset_reset(self):
        source = fixture()
        source['lag'][0]['value'][1] = '2000'
        finding = next(f for f in explain(source) if f['code'] == 'lag-dev')
        self.assertIn('증가 추세를 판단할 수 없습니다', finding['hypothesis'])
        self.assertIn('하지 마세요', finding['recommendation'])

    def test_no_candidate_does_not_claim_healthy(self):
        facts = m.project(fixture())
        self.assertIn('정상 보장은 아님', m.assessment(facts)['dev'])
        self.assertFalse(any(f['severity'] in ('critical', 'warning', 'unknown') for f in explain(fixture())))

    def test_html_escaped_and_checks_collapsed(self):
        facts = m.project(fixture())
        summary = m.explain(facts, m.assessment(facts))
        summary['findings'][0]['title'] = '<script>bad</script>'
        state = {'attempt_status': '성공', 'success': {'time': 1000, 'statuses': m.assessment(facts), 'facts': facts, 'summary': summary}}
        content = m.dashboard(state, 1000)['panels'][0]['options']['content']
        self.assertNotIn('<script>', content)
        self.assertIn('&lt;script&gt;', content)
        self.assertIn('<details><summary>확인 순서', content)

    def test_initialize_migrates_old_summary_without_network_or_new_timestamp(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(m, 'request') as network:
            root = Path(temp)/'state'
            facts = m.project(fixture())
            old = {'success': {'time': 1000, 'facts': facts, 'statuses': {}, 'summary': {'findings': [{'old': 'model output'}]}}}
            m.atomic(root/'state.json', json.dumps(old))
            m.run(root)
            after = json.loads((root/'state.json').read_text())
            self.assertEqual(after['success']['time'], 1000)
            self.assertNotIn('old', str(after['success']['summary']))
            network.assert_not_called()
            self.assertEqual((root/'state.json').stat().st_mode & 0o777, 0o600)

    def test_live_is_local_and_failure_preserves_previous_result(self):
        with tempfile.TemporaryDirectory() as temp, patch.dict(os.environ, {}, clear=True):
            root = Path(temp)/'state'
            with patch.object(m, 'collect', return_value=fixture()) as collector:
                self.assertEqual(m.run(root, live=True), 0)
                self.assertEqual(m.run(root, live=True), 0)
                collector.assert_called_once()
            previous = json.loads((root/'state.json').read_text())
            previous['attempt_time'] = 0
            m.atomic(root/'state.json', json.dumps(previous))
            with patch.object(m, 'collect', side_effect=TimeoutError):
                self.assertEqual(m.run(root, live=True), 1)
            after = json.loads((root/'state.json').read_text())
            self.assertEqual(after['success'], previous['success'])
            self.assertIn('실패', after['attempt_status'])
            content = m.dashboard(after, 2000)['panels'][0]['options']['content']
            self.assertLess(content.index('주의:'), content.index('운영 대상 수집 중'))

    def test_no_inventory_still_publishes_unknown(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(m, 'collect', return_value={}):
            root = Path(temp)/'state'
            self.assertEqual(m.run(root, live=True), 0)
            content = (root/'dashboards/kidly-ai-summary.json').read_text()
            self.assertIn('운영 대상 판단 불가', content)

    def test_proxy_environment_ignored_and_nonlocal_requests_refused(self):
        for url in ('https://api.openai.com/v1/responses', 'http://example.com:30090/api/v1/query', 'http://localhost:3000/api/v1/query'):
            with self.assertRaises(ValueError):
                m.request(url)
        builder = m.urllib.request.build_opener
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b'{}'
        def inspect(*args):
            opener = builder(*args)
            self.assertFalse(any(isinstance(h, m.urllib.request.ProxyHandler) for h in opener.handlers))
            fake = MagicMock()
            fake.open.return_value = response
            return fake
        with patch.dict(os.environ, {'http_proxy': 'http://untrusted.invalid:9999'}), patch.object(m.urllib.request, 'build_opener', side_effect=inspect):
            self.assertEqual(m.request('http://127.0.0.1:30090/api/v1/query?query=up'), {})
        with self.assertRaises(ValueError):
            m.NoRedirect().redirect_request(None)

    def test_no_secret_configuration_or_external_client_remains(self):
        text = (SOURCE/'summary.py').read_text() + (SOURCE/'kidly-ai-summary.service').read_text()
        for forbidden in ('OPENAI_', 'api.openai.com', 'Bearer ', 'EnvironmentFile='):
            self.assertNotIn(forbidden, text)


if __name__ == '__main__':
    unittest.main()
