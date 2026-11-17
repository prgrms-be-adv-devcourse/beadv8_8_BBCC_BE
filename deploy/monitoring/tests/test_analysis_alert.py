"""Alert-triggered AI analysis (Grafana webhook) and Slack summary of the published note."""
import io
import json
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import MagicMock

sys.path.insert(0, str(Path(__file__).parents[1] / 'ai-notes'))
from analysis_notify import SlackNotifier, slack_text
from analysis_requests import AnalysisRequests
from grafana_analysis_api import ALERT_PATH, AnalysisAPI, alert_trigger
from groq_once import PRINCIPAL
from storage import Store

ROOT = Path(__file__).resolve().parents[1]
TOKEN = 'a' * 43


def firing(name='5xx 증가', jobs=('kidly-order',), status='firing'):
    return {'status': status, 'commonLabels': {'alertname': name, 'env': 'dev'},
            'alerts': [{'status': status, 'labels': {'alertname': name, 'job': job, 'env': 'dev'}} for job in jobs]}


class AlertTriggerTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.token = Path(directory.name) / 'alert-token'
        self.token.write_text(TOKEN)
        self.calls = []

        def run(**kwargs):
            self.calls.append(kwargs)
            return 'published'

        self.requests = AnalysisRequests(run, lambda: 0, openai_runner=lambda **_: 'published', clock=lambda: 100)
        self.api = AnalysisAPI(None, self.requests, origin='https://grafana.example.test', alert_token_file=self.token)

    def post(self, payload, authorization='Bearer ' + TOKEN):
        headers = {'Authorization': authorization} if authorization else {}
        return self.api.dispatch('POST', ALERT_PATH, headers, json.dumps(payload).encode())

    def wait_idle(self):
        for _ in range(200):
            if self.requests.read(is_operator=True)[1]['state'] != 'running':
                return
            time.sleep(0.005)

    def test_token_is_required_and_cookie_session_is_not_used(self):
        self.assertEqual(self.post(firing(), authorization=None), (403, {'state': 'forbidden'}))
        self.assertEqual(self.post(firing(), authorization='Bearer ' + 'b' * 43), (403, {'state': 'forbidden'}))
        self.token.write_text('short')
        self.assertEqual(self.post(firing(), authorization='Bearer short'), (403, {'state': 'forbidden'}))
        self.assertEqual(self.calls, [])

    def test_firing_alert_runs_free_provider_with_matching_focus(self):
        self.assertEqual(self.post(firing('DB 연결 대기 지속', ('kidly-order', 'kidly-payment'))), (202, {'state': 'running'}))
        self.wait_idle()
        self.assertEqual(self.calls, [{'focus': 'latency'}])
        self.assertEqual(self.requests.provider, 'groq')

    def test_resolved_or_malformed_payload_never_runs(self):
        self.assertEqual(self.post(firing(status='resolved')), (202, {'state': 'ignored'}))
        self.assertEqual(self.api.dispatch('POST', ALERT_PATH, {'Authorization': 'Bearer ' + TOKEN}, b'{'),
                         (400, {'state': 'invalid_request'}))
        self.assertEqual(self.calls, [])

    def test_limited_or_running_returns_2xx_so_grafana_does_not_resend(self):
        limited = AnalysisRequests(lambda **_: 'published', lambda: 10**10, clock=lambda: 100)
        api = AnalysisAPI(None, limited, origin='x', alert_token_file=self.token)
        code, result = api.dispatch('POST', ALERT_PATH, {'Authorization': 'Bearer ' + TOKEN}, json.dumps(firing()).encode())
        self.assertEqual((code, result['state']), (202, 'limited'))

    def test_trigger_text_uses_only_validated_labels(self):
        self.assertEqual(alert_trigger(firing('ERROR 로그', ('kidly-order', 'evil<!channel>', 'kidly-order'))),
                         ('errors', '알림 "ERROR 로그" (kidly-order)'))
        self.assertEqual(alert_trigger(firing('서비스 다운', ())), ('overview', '알림 "서비스 다운"'))
        self.assertIsNone(alert_trigger(firing('x\n' * 30)))


class SlackNotifierTest(unittest.TestCase):
    def test_posts_priority_of_note_published_after_start_with_escaping(self):
        with tempfile.TemporaryDirectory() as temp:
            state = Path(temp)
            store = Store(state)
            record = {'priority': '관측: <!channel> 5xx 증가 & 지연\n먼저 할 일: 서비스 오류 화면 확인'}
            payload = json.dumps({'streams': [{'stream': {}, 'values': [['1', json.dumps(record, ensure_ascii=False)]]}]})
            with store.db:
                store.db.execute('INSERT INTO notes VALUES(?,?,?,?,?,1,?,?,?,0)',
                                 ('old', 'd', PRINCIPAL, 'r', payload.replace('5xx', 'old'), 50, 'dev', 50))
                store.db.execute('INSERT INTO notes VALUES(?,?,?,?,?,1,?,?,?,0)',
                                 ('new', 'd', PRINCIPAL, 'r', payload, 120, 'dev', 120))
            store.db.close()
            webhook = state / 'webhook'
            webhook.write_text('https://hooks.slack.com/services/T000/B000/fixture\n')
            transport = MagicMock()
            transport.open.return_value.__enter__.return_value.status = 200
            notifier = SlackNotifier(state, webhook, transport=transport)
            self.assertTrue(notifier(provider='groq', trigger='알림 "5xx 증가" (kidly-order)', since=100))
            request = transport.open.call_args.args[0]
            self.assertEqual(request.full_url, 'https://hooks.slack.com/services/T000/B000/fixture')
            text = json.loads(request.data)['text']
            self.assertIn('&lt;!channel&gt; 5xx 증가 &amp; 지연', text)
            self.assertNotIn('<!channel>', text)
            self.assertIn('Groq · 알림 "5xx 증가" (kidly-order)', text)
            self.assertFalse(notifier(provider='groq', trigger='x', since=200))

    def test_non_slack_webhook_is_never_called(self):
        with tempfile.TemporaryDirectory() as temp:
            webhook = Path(temp) / 'webhook'
            webhook.write_text('https://example.com/collect')
            transport = MagicMock()
            self.assertFalse(SlackNotifier(Path(temp), webhook, transport=transport)(provider='groq', trigger='x', since=0))
            transport.open.assert_not_called()

    def test_run_notifies_only_after_publish_and_failures_are_contained(self):
        calls = []
        def boom(**kwargs):
            calls.append(kwargs)
            raise OSError('slack down')
        for result, expected in (('published', 1), ('failed', 0)):
            calls.clear()
            handler = AnalysisRequests(lambda **_: result, lambda: 0, notifier=boom, clock=lambda: 100)
            handler.request(is_operator=True, trigger='Grafana 버튼')
            for _ in range(200):
                if handler.read(is_operator=True)[1]['state'] != 'running':
                    break
                time.sleep(0.005)
            time.sleep(0.02)
            self.assertEqual(len(calls), expected)
            self.assertEqual(handler.read(is_operator=True)[1]['state'], result)
        self.assertEqual(slack_text('a\nb', provider='openai', trigger='Grafana 버튼').splitlines()[0],
                         '🤖 AI 운영 분석 · OpenAI · Grafana 버튼')


class AlertDeploymentTest(unittest.TestCase):
    def test_webhook_route_token_and_labels_are_wired(self):
        alerting = (ROOT / 'grafana/provisioning/alerting/alerting.yml').read_text()
        self.assertIn('url: http://kidly-ai-analysis.monitoring.svc.cluster.local:8786/kidly-ai-analysis/alert', alerting)
        self.assertIn('authorization_credentials: ${AI_ALERT_TOKEN}', alerting)
        self.assertIn("- ['ai_analysis', '=', 'auto']", alerting)
        self.assertIn('continue: true', alerting)
        rules = alerting + (ROOT / 'grafana/provisioning/alerting/resources.yml').read_text()
        self.assertEqual(rules.count('ai_analysis: auto'), rules.count('ai_prompt:'))
        self.assertIn('name: ai-analysis-alert', (ROOT / 'k8s/grafana.yaml').read_text())
        deploy = (ROOT / 'deploy.sh').read_text()
        self.assertLess(deploy.index('bash ai-notes/analysis-api.sh token'), deploy.index('kubectl apply --server-side'))
        script = (ROOT / 'ai-notes/analysis-api.sh').read_text()
        self.assertIn('--from-file=AI_ALERT_TOKEN="$token_file"', script)
        self.assertIn('--alert-token-file "$token_file" --slack-webhook-file secrets/SLACK_OPS_WEBHOOK_URL', script)
        # The alert path stays internal: the public route only matches run and status.
        self.assertNotIn('/kidly-ai-analysis/alert', (ROOT / 'k8s/ai-analysis.yaml').read_text())


if __name__ == '__main__':
    unittest.main()
