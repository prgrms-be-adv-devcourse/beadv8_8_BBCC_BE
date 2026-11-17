from pathlib import Path
import subprocess
import sys
import tempfile
from threading import Event
import time
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).parents[1] / 'ai-notes'))
from analysis_requests import AnalysisRequests, next_trial_at, run_groq_trial
from groq_once import PAID_PRINCIPAL, PRINCIPAL, ProviderError, trial_lock
from storage import Store


class AnalysisRequestsTest(unittest.TestCase):
    def wait_for(self, handler, state):
        for _ in range(200):
            _, result = handler.read(is_operator=True)
            if result['state'] == state:
                return result
            time.sleep(0.005)
        self.fail(f'analysis did not finish as {state}')

    def test_operator_request_is_single_and_keeps_previous_publication_on_failure(self):
        finish = Event()
        result = 'published'

        def run():
            return result if finish.wait(1) else 'failed'

        handler = AnalysisRequests(run, lambda: 0, clock=lambda: 200)
        self.assertEqual(handler.request(is_operator=False), (403, {'state': 'forbidden'}))
        self.assertEqual(handler.read(is_operator=False), (403, {'state': 'forbidden'}))
        self.assertEqual(handler.request(is_operator=True)[0], 202)
        self.assertEqual(handler.request(is_operator=True), (409, {'state': 'running'}))
        finish.set()
        self.assertEqual(self.wait_for(handler, 'published')['published_at'], 200)
        result = 'failed'
        self.assertEqual(handler.request(is_operator=True)[0], 202)
        self.assertEqual(self.wait_for(handler, 'failed')['published_at'], 200)

    def test_manual_attempt_enforces_persistent_cooldown(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp)
            self.assertEqual(next_trial_at(path), 0)
            store = Store(path)
            with store.db:
                store.db.execute('INSERT INTO contexts VALUES (?,?,?,?,?)',
                                 ('fixture', '{}', 100, PRINCIPAL, 100))
            store.db.close()
            handler = AnalysisRequests(None, lambda: next_trial_at(path), clock=lambda: 500)
            self.assertEqual(handler.request(is_operator=True),
                             (429, {'state': 'limited', 'next_at': 3700}))
            store = Store(path)
            with store.db:
                store.db.execute('INSERT INTO contexts VALUES (?,?,?,?,?)',
                                 ('paid-fixture', '{}', 200, PAID_PRINCIPAL, 200))
            store.db.close()
            self.assertEqual(handler.request(is_operator=True),
                             (429, {'state': 'limited', 'next_at': 3800}))


    def test_manual_and_server_process_share_trial_lock(self):
        with tempfile.TemporaryDirectory() as temp, trial_lock(Path(temp)):
            with self.assertRaisesRegex(ProviderError, '^trial_running$'), trial_lock(Path(temp)):
                self.fail('second trial obtained the lock')

    def test_worker_uses_existing_command_and_never_passes_key_in_arguments(self):
        response = subprocess.CompletedProcess([], 0, stdout=b'{"ok":true}')
        with tempfile.TemporaryDirectory() as temp, patch('analysis_requests.subprocess.run', return_value=response) as run:
            key = Path(temp) / 'key'
            key.write_bytes(b'fixture-key')
            self.assertEqual(run_groq_trial(key, free_plan_confirmed=False), 'failed')
            run.assert_not_called()
            self.assertEqual(run_groq_trial(key, free_plan_confirmed=True), 'published')
            self.assertEqual(run.call_args.kwargs['input'], b'fixture-key\n')
            self.assertNotIn('fixture-key', ' '.join(run.call_args.args[0]))
