"""External monitoring state transitions; no production traffic or real Slack calls."""
import importlib.util
import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('external_probe', ROOT / 'external_probe.py')
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)
sys.modules['external_probe'] = probe
spec = importlib.util.spec_from_file_location('external_watch', ROOT / 'external_watch.py')
watch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(watch)

NOW = 1795048200  # 2026-11-19 09:30 KST


def row(ok=True, days=60):
    return {'ok': ok, 'status': 200 if ok else 503, 'tls_days': days, 'duration_ms': 10}


class ExternalWatchTests(unittest.TestCase):
    def test_window_excludes_startup_shutdown_and_late_runs(self):
        from datetime import datetime
        for hour, minute, expected in ((9, 0, False), (9, 14, False), (9, 15, True),
                                       (17, 54, True), (17, 55, False), (18, 0, False)):
            now = datetime(2026, 11, 19, hour, minute, tzinfo=watch.KST).timestamp()
            self.assertEqual(watch.operating_window(now), expected)

    def test_retry_only_failed_targets_and_wait_once(self):
        calls = []
        def check(key, *args, **kwargs):
            calls.append(key)
            return row(key != 'gateway-route' or calls.count(key) == 2)
        pauses = []
        rows = watch.probe_targets(check, pauses.append)
        self.assertTrue(all(r['ok'] for r in rows.values()))
        self.assertEqual(calls, ['gateway-route', 'product-read', 'grafana-health', 'gateway-route'])
        self.assertEqual(pauses, [30])

    def test_down_dedup_recovery_and_no_recovery_without_prior_alert(self):
        events, state = watch.transitions({'gateway-route': row(False)}, {}, NOW)
        self.assertEqual([event[1] for event in events], ['down'])
        events, state = watch.transitions({'gateway-route': row(False)}, state, NOW + 900)
        self.assertEqual(events, [])
        events, state = watch.transitions({'gateway-route': row()}, state, NOW + 1800)
        self.assertEqual([event[1] for event in events], ['recovered'])
        self.assertEqual(watch.transitions({'gateway-route': row()}, {}, NOW)[0], [])

    def test_expected_daily_shutdown_does_not_create_next_day_recovery(self):
        _, state = watch.transitions({'gateway-route': row(False)}, {}, NOW)
        self.assertEqual(watch.transitions({'gateway-route': row()}, state, NOW + 86400)[0], [])

    def test_certificate_warning_dedup_and_renewal(self):
        events, state = watch.transitions({'gateway-route': row(days=14)}, {}, NOW)
        self.assertEqual([e[1] for e in events], ['certificate'])
        self.assertEqual(watch.transitions({'gateway-route': row(days=13)}, state, NOW + 900)[0], [])
        events, pending = watch.transitions({'gateway-route': row(False, None)}, state, NOW + 900)
        self.assertEqual([e[1] for e in events], ['down'])
        self.assertTrue(pending['targets']['gateway-route']['certificate'])
        events, _ = watch.transitions({'gateway-route': row(days=60)}, state, NOW + 1800)
        self.assertEqual([e[1] for e in events], ['renewed'])

    def test_safe_slack_message_never_includes_raw_error(self):
        fake = row(False)
        fake['error'] = 'private fixture'
        text = watch.message(('gateway-route', 'down', fake), 'https://github.com/owner/repo/actions/runs/1', True)
        self.assertTrue(text.startswith('[시험]'))
        self.assertIn('Gateway', text)
        self.assertNotIn('private fixture', text)
        self.assertNotIn('업무 복구', text)
        recovered = watch.message(('gateway-route', 'recovered', row()), 'fixture', True)
        self.assertIn('보장하지 않습니다', recovered)

    def test_previous_state_reads_only_workflow_artifact(self):
        _, state = watch.transitions({'gateway-route': row(False)}, {}, NOW)
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('state.json', json.dumps(state))
        data = [{'workflow_runs': [{'id': 1, 'event': 'schedule'}]},
                {'artifacts': [{'id': 2, 'name': watch.ARTIFACT, 'expired': False}]}, buffer.getvalue()]
        with patch.object(watch, 'gh_json', side_effect=data) as api:
            self.assertEqual(watch.previous_state('owner/repo', NOW + 900), state)
            self.assertIn('/workflows/external-observation.yml/runs?', api.call_args_list[0].args[0])
        with patch.object(watch, 'gh_json', side_effect=[{'workflow_runs': []}]):
            self.assertEqual(watch.previous_state('owner/repo', NOW), {})

    def test_previous_state_skips_expired_and_old_artifacts(self):
        with patch.object(watch, 'gh_json', side_effect=[
                {'workflow_runs': [{'id': 1, 'event': 'schedule'}]},
                {'artifacts': [{'id': 2, 'name': watch.ARTIFACT, 'expired': True}]}]):
            self.assertEqual(watch.previous_state('owner/repo', NOW), {})
        _, state = watch.transitions({'gateway-route': row(False)}, {}, NOW - 86401)
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('state.json', json.dumps(state))
        with patch.object(watch, 'gh_json', side_effect=[
                {'workflow_runs': [{'id': 1, 'event': 'schedule'}]},
                {'artifacts': [{'id': 2, 'name': watch.ARTIFACT, 'expired': False}]}, buffer.getvalue()]):
            self.assertEqual(watch.previous_state('owner/repo', NOW), {})

    def test_previous_state_rejects_non_boolean_flags(self):
        _, state = watch.transitions({'gateway-route': row(False)}, {}, NOW)
        state['targets']['gateway-route']['down'] = 'false'
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as archive:
            archive.writestr('state.json', json.dumps(state))
        with patch.object(watch, 'gh_json', side_effect=[
                {'workflow_runs': [{'id': 1, 'event': 'schedule'}]},
                {'artifacts': [{'id': 2, 'name': watch.ARTIFACT, 'expired': False}]}, buffer.getvalue()]):
            with self.assertRaisesRegex(RuntimeError, '형식 확인'):
                watch.previous_state('owner/repo', NOW)

    def test_slack_errors_never_reveal_webhook(self):
        with patch.dict(watch.os.environ, {'SLACK_OPS_WEBHOOK_URL': 'https://hooks.slack.com/services/fixture/secret'}), \
             patch.object(watch.urllib.request, 'build_opener', side_effect=ValueError('fixture/secret')):
            with self.assertRaisesRegex(RuntimeError, '^운영 Slack 전송 실패$'):
                watch.send_slack('fixture')

    def test_untrusted_webhook_origin_is_refused(self):
        with patch.dict(watch.os.environ, {'SLACK_OPS_WEBHOOK_URL': 'https://elsewhere.invalid/services/fixture'}):
            with self.assertRaisesRegex(RuntimeError, '설정 확인'):
                watch.send_slack('fixture')
