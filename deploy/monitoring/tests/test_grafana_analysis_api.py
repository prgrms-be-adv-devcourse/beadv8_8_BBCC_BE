import io
import json
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).parents[1] / 'ai-notes'))
from analysis_requests import AnalysisRequests
from grafana_analysis_api import ACTION_PATH, STATUS_PATH, AnalysisAPI, GrafanaAuthorization, write_dashboard_status


ORIGIN = 'https://grafana.example.test'


class GrafanaAnalysisAPITest(unittest.TestCase):
    def setup_api(self, orgs):
        transport = Mock()
        transport.open.side_effect = lambda *_args, **_kwargs: io.BytesIO(json.dumps(orgs).encode())
        auth = GrafanaAuthorization('http://grafana.example.test:3000', transport=transport)
        requests = AnalysisRequests(lambda **_: 'published', lambda: 0)
        return AnalysisAPI(auth, requests, origin=ORIGIN), transport

    def test_admin_cookie_is_verified_and_other_cookies_are_ignored(self):
        api, transport = self.setup_api([{'orgId': 1, 'role': 'Admin'}])
        code, result = api.dispatch('POST', ACTION_PATH, {
            'Origin': ORIGIN, 'Cookie': 'grafana_session=fixture; other=private',
        })
        self.assertEqual((code, result), (202, {'state': 'running'}))
        self.assertEqual(api.dispatch('GET', STATUS_PATH, {'Cookie': 'grafana_session=fixture'})[0], 200)
        request = transport.open.call_args.args[0]
        self.assertEqual(request.full_url, 'http://grafana.example.test:3000/api/user/orgs')
        self.assertEqual(request.get_header('Cookie'), 'grafana_session=fixture')

    def test_viewer_and_other_org_admin_cannot_run_or_read(self):
        api, _ = self.setup_api([{'orgId': 1, 'role': 'Viewer'}, {'orgId': 2, 'role': 'Admin'}])
        headers = {'Origin': ORIGIN, 'Cookie': 'grafana_session=fixture', 'X-Grafana-Org-Role': 'Admin'}
        self.assertEqual(api.dispatch('POST', ACTION_PATH, headers)[0], 403)
        self.assertEqual(api.dispatch('GET', STATUS_PATH, headers)[0], 403)

    def test_cross_origin_and_unexpected_payload_never_authorize(self):
        api, transport = self.setup_api([{'orgId': 1, 'role': 'Admin'}])
        for headers, expected in (({}, 403), ({'Origin': 'https://other.test'}, 403),
                                  ({'Origin': ORIGIN, 'Content-Length': '2'}, 400)):
            self.assertEqual(api.dispatch('POST', ACTION_PATH, headers)[0], expected)
        transport.open.assert_not_called()

    def test_rejected_session_and_grafana_failure_are_distinct(self):
        api, transport = self.setup_api([])
        headers = {'Cookie': 'grafana_session=fixture'}
        for upstream, expected in ((401, 403), (503, 503)):
            transport.open.side_effect = HTTPError('http://grafana.example.test', upstream,
                                                   'fixture', {}, None)
            self.assertEqual(api.dispatch('GET', STATUS_PATH, headers)[0], expected)

    def test_paid_provider_requires_enablement_and_per_request_confirmation(self):
        api, _ = self.setup_api([{'orgId': 1, 'role': 'Admin'}])
        headers = {'Origin': ORIGIN, 'Cookie': 'grafana_session=fixture',
                   'X-Kidly-AI-Provider': 'openai'}
        self.assertEqual(api.dispatch('POST', ACTION_PATH, headers), (400, {'state': 'provider_unavailable'}))
        groq, openai = Mock(return_value='published'), Mock(return_value='failed')
        api.requests = AnalysisRequests(groq, lambda: 0, openai_runner=openai)
        self.assertEqual(api.dispatch('POST', ACTION_PATH, headers),
                         (400, {'state': 'paid_confirmation_required'}))
        groq.assert_not_called()
        openai.assert_not_called()
        headers['X-Kidly-AI-Confirm-Paid'] = 'true'
        headers['X-Kidly-AI-Focus'] = 'free text'
        self.assertEqual(api.dispatch('POST', ACTION_PATH, headers), (400, {'state': 'invalid_focus'}))
        openai.assert_not_called()
        headers['X-Kidly-AI-Focus'] = 'latency'
        self.assertEqual(api.dispatch('POST', ACTION_PATH, headers)[0], 202)
        for _ in range(100):
            result = api.requests.read(is_operator=True)[1]
            if result['state'] == 'failed':
                break
            time.sleep(0.005)
        self.assertEqual((result['state'], result['provider']), ('failed', 'openai'))
        openai.assert_called_once_with(focus='latency')
        groq.assert_not_called()

    def test_grafana_empty_json_action_and_dashboard_status_metrics(self):
        api, _ = self.setup_api([{'orgId': 1, 'role': 'Admin'}])
        headers = {'Origin': ORIGIN, 'Cookie': 'grafana_session=fixture', 'Content-Length': '2'}
        self.assertEqual(api.dispatch('POST', ACTION_PATH, headers, b'[]')[0], 400)
        self.assertEqual(api.dispatch('POST', ACTION_PATH, headers, b'{}')[0], 202)
        with tempfile.TemporaryDirectory() as directory:
            metrics = Path(directory) / 'status.prom'
            write_dashboard_status(metrics, {'state': 'running', 'providers': ['groq']}, 100)
            self.assertEqual(metrics.read_text().splitlines(), [
                'kidly_ai_analysis_heartbeat_seconds 100',
                'kidly_ai_analysis_state 1',
                'kidly_ai_analysis_provider_enabled{provider="groq"} 1',
            ])
            write_dashboard_status(metrics, {'state': 'published', 'providers': ['groq', 'openai'],
                                             'published_at': 101}, 102)
            self.assertIn('kidly_ai_analysis_provider_enabled{provider="openai"} 1', metrics.read_text())
            self.assertIn('kidly_ai_analysis_published_seconds 101', metrics.read_text())
            self.assertNotIn('kidly_ai_analysis_state 1', metrics.read_text())

    def test_dashboard_uses_existing_prometheus_and_same_origin_post(self):
        dashboard_path = Path(__file__).parents[1] / 'grafana/dashboards/kidly-ai-notes.json'
        dashboard = json.loads(dashboard_path.read_text())
        panels = {panel['id']: panel for panel in dashboard['panels']}
        priority, run, status, history, details, shortcuts = (panels[id] for id in (3, 201, 202, 205, 5, 206))
        self.assertLess(panels[1]['gridPos']['y'], run['gridPos']['y'])
        self.assertIn('kidly_ai_notes_last_observed_seconds', panels[208]['targets'][0]['expr'])
        # Links live next to the run button instead of a separate link row under the selectors.
        self.assertEqual(dashboard['links'], [])
        self.assertEqual(shortcuts['type'], 'text')
        self.assertEqual(shortcuts['gridPos']['y'], run['gridPos']['y'])
        self.assertIn('/d/kidly-home', shortcuts['options']['content'])
        self.assertIn('/d/kidly-http?var-env=${env}', shortcuts['options']['content'])
        self.assertTrue(history['collapsed'])
        self.assertEqual(history['panels'][0]['targets'][0]['maxLines'], 15)
        self.assertIn(['X-Kidly-AI-Focus', '${analysis_focus:raw}'],
                      run['fieldConfig']['overrides'][0]['properties'][1]['value'][0]['fetch']['headers'])
        # Freshness precedes the action controls, followed by the recorded proposal.
        self.assertEqual(run['gridPos']['y'], status['gridPos']['y'])
        self.assertLess(run['gridPos']['y'], priority['gridPos']['y'])
        self.assertIn('{{if .priority}}{{.priority}}{{else}}{{.headline}}',
                      priority['targets'][0]['expr'])
        self.assertTrue(details['collapsed'])
        self.assertIn('{{.report}}', details['panels'][0]['targets'][0]['expr'])
        provider = next(variable for variable in dashboard['templating']['list'] if variable['name'] == 'provider')
        self.assertFalse(provider['multi'])
        self.assertFalse(provider['includeAll'])
        self.assertEqual(provider['current']['value'], 'groq')
        self.assertIn('kidly_ai_analysis_provider_enabled', provider['query'])
        self.assertEqual((run['datasource']['uid'], status['datasource']['uid']), ('prometheus', 'prometheus'))
        actions = run['fieldConfig']['overrides'][0]['properties'][1]['value']
        # One run button; Slack only receives alert-triggered analysis (things a person must act on)
        self.assertEqual([item['title'] for item in actions], ['새 분석 실행'])
        self.assertIn('Slack에는 올리지 않습니다', actions[0]['confirmation'])
        action = actions[0]
        self.assertIn(['X-Kidly-AI-Provider', '${provider:raw}'], action['fetch']['headers'])
        self.assertIn('청구', action['confirmation'])
        self.assertEqual((action['fetch']['url'], action['fetch']['method']), (ACTION_PATH, 'POST'))
        self.assertIn('kidly_ai_analysis_provider_enabled{provider="${provider:raw}"}', run['targets'][0]['expr'])
        self.assertIn('kidly_ai_analysis_heartbeat_seconds', status['targets'][0]['expr'])
