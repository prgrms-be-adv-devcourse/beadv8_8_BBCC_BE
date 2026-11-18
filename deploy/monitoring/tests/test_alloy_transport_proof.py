import importlib.util
import json
from pathlib import Path
import re
import unittest
from unittest.mock import MagicMock, Mock, patch
from urllib.error import HTTPError


module = Path(__file__).resolve().parents[2] / 'tracing-ops' / 'verify-alloy.py'
spec = importlib.util.spec_from_file_location('verify_alloy', module)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)
TRACE_ID = '1234567890abcdef1234567890abcdef'
ALLOY = Path(__file__).resolve().parents[2] / 'tracing-ops' / 'app-alloy' / 'otlp.alloy'


def span_statements():
    text = ALLOY.read_text()
    block = text[text.index('context = "span"'):]
    block = block[:block.index('\n    ]\n')]
    return re.findall(r'^ +`(.+)`,$', block, re.M)


def stored(result):
    result['resourceSpans'][0]['scopeSpans'][0]['spans'][0]['name'] = probe.EXPECTED_NAME
    # Tempo 가 속성 순서를 바꿔도 받는다
    result['resourceSpans'][0]['scopeSpans'][0]['spans'][0]['attributes'] = list(reversed(probe.EXPECTED_ATTRIBUTES))
    return result


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
        result = stored(probe.fixture(TRACE_ID, 1000000))
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
        with self.assertRaises(probe.OrderTagMismatch):
            probe.verify(client, 'http://127.0.0.1:3200', TRACE_ID)

    def test_check_rejects_old_name_or_extra_attribute(self):
        for change in ('name', 'extra'):
            client = MagicMock()
            response = client.open.return_value.__enter__.return_value
            response.status = 200
            result = stored(probe.fixture(TRACE_ID, 1000000))
            span = result['resourceSpans'][0]['scopeSpans'][0]['spans'][0]
            if change == 'name':
                span['name'] = 'HTTP'
            else:
                span['attributes'].append({'key': 'url.full', 'value': {'stringValue': 'http://x/api/v1/orders/1'}})
            response.read.return_value = json.dumps(result)
            with self.subTest(change), self.assertRaises(probe.OrderTagMismatch):
                probe.verify(client, 'http://127.0.0.1:3200', TRACE_ID)

    def test_server_span_name_uses_method_and_route_and_keeps_only_safe_attributes(self):
        statements = span_statements()
        keep = [s for s in statements if s.startswith('keep_keys(')]
        self.assertEqual(len(keep), 1)
        self.assertEqual(statements[-1], keep[0])
        kept = set(re.findall(r'"([^"]+)"', keep[0]))
        self.assertEqual(kept, {'http.response.status_code', 'http.status_code', 'kidly.operation',
                                'http.route', 'http.request.method'})
        # 원래 URL · 쿼리 · SQL · 헤더는 남기지 않는다
        self.assertFalse(any(key.startswith(('url.', 'http.request.header', 'http.response.header', 'db.'))
                             or 'query' in key or 'statement' in key for key in kept))
        names = [s for s in statements if s.startswith('set(name,')]
        route = [s for s in names if 'Concat(' in s]
        self.assertEqual(len(route), 2)
        # 기존 이름 규칙(HTTP · JDBC …) 뒤에 덮어쓰고, 서버 span 에 경로 템플릿이 있을 때만
        self.assertEqual(names[-2:], route)
        for statement in route:
            self.assertIn('kind == SPAN_KIND_SERVER', statement)
            self.assertIn('attributes["http.route"] != nil', statement)
        self.assertIn('attributes["http.request.method"], attributes["http.route"]', route[0])
        self.assertIn('attributes["http.method"], attributes["http.route"]', route[1])
        self.assertIn('attributes["http.request.method"] == nil', route[1])

    def test_check_rejects_attribute_without_key(self):
        client = MagicMock()
        response = client.open.return_value.__enter__.return_value
        response.status = 200
        result = probe.fixture(TRACE_ID, 1000000)
        result['resourceSpans'][0]['scopeSpans'][0]['spans'][0]['attributes'] = [
            {'value': {'stringValue': 'order.detail'}},
        ]
        response.read.return_value = json.dumps(result)
        with self.assertRaises(probe.OrderTagMismatch):
            probe.verify(client, 'http://127.0.0.1:3200', TRACE_ID)
