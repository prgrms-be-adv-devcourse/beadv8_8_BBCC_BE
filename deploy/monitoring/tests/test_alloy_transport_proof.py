import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import MagicMock, Mock, patch
from urllib.error import HTTPError


module = Path(__file__).resolve().parents[2] / 'tracing-ops' / 'verify-alloy.py'
spec = importlib.util.spec_from_file_location('verify_alloy', module)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)
TRACE_ID = '1234567890abcdef1234567890abcdef'


class AlloyTransportProofTest(unittest.TestCase):
    def test_send_only_one_synthetic_span(self):
        client = MagicMock()
        response = client.open.return_value.__enter__.return_value
        response.status = 200
        response.read.return_value = b'{}'
        probe.send(client, 'http://127.0.0.1:4318', TRACE_ID)
        request = client.open.call_args.args[0]
        self.assertEqual(request.get_method(), 'POST')
        resource, = json.loads(request.data)['resourceSpans']
        scope, = resource['scopeSpans']
        span, = scope['spans']
        self.assertIn({'key': 'service.name', 'value': {'stringValue': 'kidly-order'}},
                      resource['resource']['attributes'])
        self.assertEqual(span['traceId'], TRACE_ID)
        self.assertEqual(scope['scope']['name'], 'kidly.tracing.synthetic')
        self.assertEqual(span['attributes'], [
            {'key': 'http.request.method', 'value': {'stringValue': 'GET'}},
            {'key': 'http.route', 'value': {'stringValue': '/api/v1/orders/{orderId}'}},
        ])

    def test_check_retries_missing_exact_trace(self):
        client = Mock()
        response = Mock()
        result = probe.fixture(TRACE_ID, 1000000)
        result['resourceSpans'][0]['scopeSpans'][0]['spans'][0]['attributes'] = [
            {'key': 'kidly.operation', 'value': {'stringValue': 'order.detail'}},
        ]
        response.__enter__ = Mock(return_value=Mock(status=200, read=Mock(return_value=json.dumps({
            'batches': result['resourceSpans'],
        }))))
        response.__exit__ = Mock()
        url = 'http://127.0.0.1:3200/api/traces/' + TRACE_ID
        client.open.side_effect = [HTTPError(url, 404, 'fixture missing', {}, None), response]
        with patch.object(probe.time, 'sleep'):
            probe.verify(client, 'http://127.0.0.1:3200', TRACE_ID)
        self.assertEqual([call.args[0].full_url for call in client.open.call_args_list], [url, url])

    def test_check_rejects_unclassified_trace(self):
        client = MagicMock()
        response = client.open.return_value.__enter__.return_value
        response.status = 200
        response.read.return_value = json.dumps(probe.fixture(TRACE_ID, 1000000))
        with self.assertRaisesRegex(RuntimeError, 'expected operation tag'):
            probe.verify(client, 'http://127.0.0.1:3200', TRACE_ID)
