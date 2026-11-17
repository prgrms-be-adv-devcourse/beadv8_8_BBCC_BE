import io
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import Mock
from urllib.error import HTTPError

sys.path.insert(0, str(Path(__file__).parents[1] / 'ai-notes'))
from analysis_requests import AnalysisRequests
from grafana_analysis_api import ACTION_PATH, STATUS_PATH, AnalysisAPI, GrafanaAuthorization


ORIGIN = 'https://grafana.example.test'


class GrafanaAnalysisAPITest(unittest.TestCase):
    def setup_api(self, orgs):
        transport = Mock()
        transport.open.side_effect = lambda *_args, **_kwargs: io.BytesIO(json.dumps(orgs).encode())
        auth = GrafanaAuthorization('http://grafana.example.test:3000', transport=transport)
        requests = AnalysisRequests(lambda: 'published', lambda: 0)
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
