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
from groq_provider import ANALYSIS_FOCUS, GroqProvider, ProviderError, MODEL, ENDPOINT, NoRedirect, groq_limitations
from openai_provider import OpenAIProvider
from groq_once import run_once, read_key, PRINCIPAL
from storage import Store, LokiPublisher, deliver_accepted
import snapshot
from errors import NotesError


def context(now=2000):
    return AnalysisContext(1, 'trial-context', now-1800, now, ('dev',),
                           (Evidence('f0', 'host_memory_available', 8, 'percent', 'global'),),
                           ('Host aggregate only',))


def body(**changes):
    result = {'finding': {'observation': 'Memory available is 8 percent',
                           'hypothesis': 'Possible memory pressure, not confirmed',
                           'check': 'Check the affected host',
                           'recommendation': 'Measure peak before changing capacity', 'evidence_id': 'f0'},
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
    def test_http_comparison_uses_adjacent_windows_and_does_not_invent_missing_values(self):
        definitions = snapshot.queries('dev')
        by_expression = {expr: name for name, (_, expr) in definitions.items()}
        readings = {
            'requests_30m': (20, 40),
            'errors_30m': (None, 2),
            'duration_sum_30m': (2.4, 11.2),
        }

        def rows(expression, when):
            value = readings.get(by_expression[expression], (None, None))[when == 3600]
            return [] if value is None else [{'metric': {'job': 'kidly-order'}, 'value': [when, value]}]

        with patch('snapshot.metric_rows', side_effect=rows) as read:
            sample = snapshot.collect('dev', 3600, compare_http=True)
        values = {fact.metric: fact.value for fact in sample.evidence}
        self.assertEqual(values['requests_previous_30m'], 20)
        self.assertEqual(values['mean_response_previous_30m'], 120)
        self.assertEqual(values['mean_response_current_30m'], 280)
        self.assertNotIn('errors_previous_30m', values)
        self.assertEqual([call.args[1] for call in read.call_args_list[-3:]], [1800] * 3)

    def test_groq_payload_can_hold_comparison_for_all_services(self):
        definitions = snapshot.queries('dev')
        http = {definitions[name][1] for name in snapshot.HTTP_COMPARISON}
        app_expected = definitions['app_expected'][1]
        globals_ = {expr for name, (_, expr) in definitions.items()
                    if name.startswith('host_') or name in (
                        'kafka_lag', 'environment_transition', 'environment_enabled', 'transition_age')}

        def rows(expression, when):
            assigned = 123.456789012345 if expression in http else 1
            if expression in globals_:
                return [{'metric': {}, 'value': [when, assigned]}]
            label = 'app_job' if expression == app_expected else 'job'
            return [{'metric': {label: name}, 'value': [when, assigned]} for name in snapshot.JOBS]

        exceptions = [{'metric': {'service_name': name, 'error_type': 'org.example.VeryLongExceptionClassName' + str(i) + 'X' * 40},
                       'value': [2000, '12345']} for i, name in enumerate(snapshot.JOBS[:snapshot.EXCEPTION_TOP])]
        with patch('snapshot.metric_rows', side_effect=rows), patch('snapshot.log_rows', return_value=exceptions):
            sample = snapshot.collect('dev', 2000, compare_http=True)
        self.assertEqual(len([f for f in sample.evidence if f.metric.startswith('error_type_30m:')]), snapshot.EXCEPTION_TOP)
        client = transport()
        self.provider(client).analyze(sample, 2300)
        request = client.open.call_args.args[0]
        self.assertLessEqual(len(request.data), 24000)
        message = json.loads(json.loads(request.data)['messages'][1]['content'])
        self.assertEqual(len([f for f in message['evidence'] if f['metric'] == 'mean_response_previous_30m']),
                         len(snapshot.JOBS) - 1)

    def test_gateway_latency_is_never_sent_and_sse_paths_are_excluded(self):
        definitions = snapshot.queries('dev')
        for name in ('requests_30m', 'errors_30m', 'duration_sum_30m'):
            self.assertIn('notifications/stream', definitions[name][1])
        self.assertIn('job!~"kidly-gateway"', definitions['duration_sum_30m'][1])

        def rows(expression, when):
            # Even if Prometheus returned a Gateway duration row, it must not become evidence.
            if 'http_server_requests' not in expression:
                return []
            return [{'metric': {'job': job}, 'value': [when, 10]} for job in ('kidly-gateway', 'kidly-order')]

        with patch('snapshot.metric_rows', side_effect=rows):
            sample = snapshot.collect('dev', 3600, compare_http=True)
        gateway = {fact.metric for fact in sample.evidence if fact.service == 'kidly-gateway'}
        self.assertIn('requests_30m', gateway)
        self.assertIn('requests_previous_30m', gateway)
        self.assertFalse({'duration_sum_30m', 'mean_response_current_30m', 'mean_response_previous_30m'} & gateway)
        self.assertIn('mean_response_current_30m', {fact.metric for fact in sample.evidence if fact.service == 'kidly-order'})
        self.assertTrue(any('SSE' in line for line in sample.limitations))

    def test_exception_classes_become_bounded_evidence_without_messages(self):
        loki = {'status': 'success', 'data': {'resultType': 'vector', 'result': [
            {'metric': {'service_name': 'kidly-recommend',
                        'error_type': 'org.springframework.transaction.CannotCreateTransactionException'}, 'value': [3600, '379']},
            {'metric': {'service_name': 'kidly-order', 'error_type': 'com.x.Outer$Inner'}, 'value': [3600, '2']},
            {'metric': {'service_name': 'kidly-order', 'error_type': 'org.y.Outer$Other'}, 'value': [3600, '3']},
            {'metric': {'service_name': 'kidly-unknown', 'error_type': 'java.io.IOException'}, 'value': [3600, '5']},
            {'metric': {'service_name': 'kidly-order', 'error_type': 'bad name; DROP'}, 'value': [3600, '9']},
        ]}}
        with patch('snapshot.metric_rows', return_value=[]), patch('snapshot.log_rows', return_value=loki['data']['result']) as read:
            sample = snapshot.collect('dev', 3600)
        facts = {(fact.metric, fact.service): fact.value for fact in sample.evidence}
        self.assertEqual(facts, {('error_type_30m:CannotCreateTransactionException', 'kidly-recommend'): 379,
                                 ('error_type_30m:Outer', 'kidly-order'): 5})
        self.assertEqual(len([f for f in sample.evidence if f.metric == 'error_type_30m:Outer']), 1)
        self.assertIn('topk(8,', read.call_args.args[0])
        self.assertTrue(any(line.startswith('error_type_30m:<Class>') for line in sample.limitations))
        with patch('snapshot.metric_rows', return_value=[]), patch('snapshot.log_rows', side_effect=OSError):
            failed = snapshot.collect('dev', 3600)
        self.assertIn('ERROR log exception types were unavailable; this is not zero.', failed.limitations)

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

    def test_focused_prompt_uses_only_the_selected_preset(self):
        for focus in ('latency', 'errors', 'resources'):
            with self.subTest(focus=focus):
                client = transport()
                provider = GroqProvider('fixture-key-for-model-test', focus=focus,
                                        free_tier_confirmed=True, clock=lambda: 2001, transport=client)
                provider.analyze(context(), 2300)
                message = json.loads(client.open.call_args.args[0].data)['messages'][0]['content']
                self.assertTrue(message.endswith(ANALYSIS_FOCUS[focus][1]))
        with self.assertRaisesRegex(ProviderError, '^invalid_focus$'):
            GroqProvider('fixture-key-for-model-test', focus='unsupported', free_tier_confirmed=True)

    def test_openai_uses_explicit_confirmation_and_own_request(self):
        key = 'fixture-openai-key-for-tests'
        sample = json.loads(body())
        sample['model'] = OpenAIProvider.model
        client = transport(json.dumps(sample).encode())
        with self.assertRaisesRegex(ProviderError, '^paid_confirmation_required$'):
            OpenAIProvider(key, transport=client)
        client.open.assert_not_called()
        report = OpenAIProvider(key, paid_confirmed=True, clock=lambda: 2001,
                                transport=client).analyze(context(), 2300)
        self.assertEqual((report.provider, report.model), ('OpenAI', OpenAIProvider.model))
        client.open.assert_called_once()
        request = client.open.call_args.args[0]
        self.assertEqual(request.full_url, OpenAIProvider.endpoint)
        payload = json.loads(request.data)
        self.assertFalse(payload['store'])
        self.assertEqual(payload['max_completion_tokens'], 600)
        self.assertNotIn('reasoning_effort', payload)

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
        fields = payload['response_format']['json_schema']['schema']['properties']['finding']['properties']
        self.assertEqual(fields['evidence_id']['enum'], ['f0'])
        self.assertEqual(fields['check']['type'], 'string')
        self.assertNotIn('test-not-a-real-api-key', request.data.decode())
        client.open.assert_called_once()

    def test_mcp_only_service_metrics_stay_out_of_groq_input(self):
        extra = (Evidence('f1', 'error_logs_30m', 3, 'estimated_count', 'dev', 'kidly-order'),
                 Evidence('f2', 'restarts_30m', 1, 'count', 'dev', 'kidly-order'),
                 Evidence('f3', 'kafka_lag_by_service', 12, 'count', 'dev', 'kidly-order'))
        client = transport()
        self.provider(client).analyze(replace(context(), evidence=context().evidence+extra), 2300)
        payload = json.loads(client.open.call_args.args[0].data)
        sent = json.loads(payload['messages'][1]['content'])
        self.assertEqual([item['evidence_id'] for item in sent['evidence']], ['f0'])
        fields = payload['response_format']['json_schema']['schema']['properties']['finding']['properties']
        self.assertEqual(fields['evidence_id']['enum'], ['f0'])
        self.assertEqual(sent['limitations'], ['Host aggregate only'])
        mixed = ('No finite fresh/available result for: kafka_lag, restarts_30m, error_logs_30m; absence is not healthy zero.',
                 'No finite fresh/available result for: restarts_30m; absence is not healthy zero.',
                 'Kafka lag is one snapshot; it does not establish increasing lag.',
                 'kafka_lag_by_service maps consumer group kidly.<domain> to kidly-<domain>.')
        self.assertEqual(groq_limitations(mixed), [
            'No finite fresh/available result for: kafka_lag; absence is not healthy zero.',
            'Kafka lag is one snapshot; it does not establish increasing lag.'])
        only_extra = replace(context(), evidence=extra)
        with self.assertRaisesRegex(ProviderError, '^insufficient_evidence$'):
            self.provider(transport()).analyze(only_extra, 2300)

    def test_rejects_actual_trial_health_claim(self):
        sample = json.loads(json.loads(body())['choices'][0]['message']['content'])
        data = replace(context(), evidence=(Evidence('f0', 'up', 1, 'boolean', 'dev', 'kidly-gateway'),))
        for field in ('observation', 'hypothesis', 'check', 'recommendation', 'limitations'):
            with self.subTest(field=field):
                candidate = json.loads(json.dumps(sample))
                claim = '모든 서비스가 정상 실행 중입니다. 추가 조치 불필요'
                if field == 'limitations':
                    candidate[field] = [claim]
                else:
                    candidate['finding'][field] = claim
                with self.assertRaisesRegex(ProviderError, '^response_unsupported_health_claim$'):
                    self.provider(transport(body(**candidate))).analyze(data, 2300)
        sample['finding']['hypothesis'] = '전반적인 시스템 안정성 확보'
        with self.assertRaisesRegex(ProviderError, '^response_unsupported_health_claim$'):
            self.provider(transport(body(**sample))).analyze(data, 2300)

    def test_rejects_low_traffic_trial_health_claim_with_conjunction(self):
        data = replace(context(), evidence=(
            Evidence('f0', 'requests_30m', 28.9966, 'estimated_count', 'dev', 'kidly-gateway'),
        ))
        sample = json.loads(json.loads(body())['choices'][0]['message']['content'])
        sample['finding']['observation'] = '모든 서비스가 정상 가동 중이지만 요청량이 매우 낮음'
        with self.assertRaisesRegex(ProviderError, '^response_unsupported_health_claim$'):
            self.provider(transport(body(**sample))).analyze(data, 2300)

    def test_accepts_scoped_reports_for_operational_fixtures(self):
        cases = (
            ('environment_enabled', 0, 'boolean', 'dev', None, 'dev 운영 대상이 꺼져 있음', '환경 상태에서 중지 기록 확인'),
            ('environment_transition', 1, 'boolean', 'dev', None, 'dev 환경 전환 중', '배포 진행 상태 확인'),
            ('requests_30m', 1, 'estimated_count', 'dev', 'kidly-gateway', 'Gateway 요청 추정치 1건', '서비스 오류 화면에서 요청 수 확인'),
            ('up', 1, 'boolean', 'dev', 'kidly-gateway', 'Gateway 지표 수집 성공', '서비스 오류 화면에서 HTTP 요청 지표 유무 확인'),
            ('host_memory_available', 8, 'percent', 'global', None, '관측된 호스트 최소 가용 메모리 8%', '서버 화면에서 호스트별 MemAvailable 확인'),
        )
        for metric, value, unit, env, service, observation, check in cases:
            with self.subTest(metric=metric):
                data = replace(context(), evidence=(Evidence('f0', metric, value, unit, env, service),))
                sample = {'observation': observation, 'hypothesis': '현재 근거로는 전체 서비스가 정상인지 판단할 수 없음',
                          'check': check, 'recommendation': '추가 확인 후 대응 여부 판단', 'evidence_id': 'f0'}
                report = self.provider(transport(body(finding=sample))).analyze(data, 2300)
                self.assertEqual(report.findings[0].checks, (check,))

    def test_no_evidence_does_not_call_model(self):
        client = transport()
        with self.assertRaisesRegex(ProviderError, '^insufficient_evidence$'):
            self.provider(client).analyze(replace(context(), evidence=()), 2300)
        client.open.assert_not_called()

    def test_empty_check_missing_field_and_unknown_contract_error_are_rejected(self):
        valid = json.loads(json.loads(body())['choices'][0]['message']['content'])
        invalid = json.loads(json.dumps(valid)); invalid['finding']['check'] = ''
        missing = json.loads(json.dumps(valid)); del missing['finding']['check']
        for data, code in ((invalid, 'response_text_invalid'), (missing, 'response_shape_invalid')):
            with self.subTest(code=code), self.assertRaisesRegex(ProviderError, '^'+code+'$'):
                self.provider(transport(body(**data))).analyze(context(), 2300)
        with patch('groq_provider.validate_report', side_effect=ValueError('unknown validator message')):
            with self.assertRaisesRegex(ProviderError, '^response_contract_invalid$'):
                self.provider(transport()).analyze(context(), 2300)

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
        bad_ref = json.loads(json.dumps(valid)); bad_ref['finding']['evidence_id'] = 'invented'
        sensitive = json.loads(json.dumps(valid)); sensitive['finding']['recommendation'] = 'password=private'
        for raw, code in ((b'not JSON', 'response_json_invalid'), (b'x'*65537, 'invalid_provider_response'),
                          (body(**bad_ref), 'response_evidence_invalid'), (body(**sensitive), 'response_text_unsafe'),
                          (body(findings=[]), 'response_shape_invalid')):
            with self.subTest(size=len(raw)), self.assertRaisesRegex(ProviderError, '^'+code+'$'):
                self.provider(transport(raw)).analyze(context(), 2300)

    def test_truncation_refusal_and_wrong_model_rejected(self):
        for field, value, code in [('model', 'other-model', 'response_model_mismatch'),
                                   ('finish_reason', 'length', 'response_truncated'),
                                   ('refusal', 'no', 'response_refused')]:
            envelope = json.loads(body())
            if field == 'model':envelope[field] = value
            elif field == 'finish_reason':envelope['choices'][0][field] = value
            else:envelope['choices'][0]['message'][field] = value
            with self.subTest(field=field), self.assertRaisesRegex(ProviderError, '^'+code+'$'):
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
