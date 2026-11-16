#!/usr/bin/env python3
"""Render a Grafana panel link for the isolated order tracing trial."""

import json
from urllib.parse import quote

TEMPO_UID = 'tempo-dev-trial'


def main():
    pane = {
        'order': {
            'datasource': TEMPO_UID,
            'queries': [{
                'refId': 'A',
                'datasource': {'type': 'tempo', 'uid': TEMPO_UID},
                'queryType': 'traceql',
                'query': '{ resource.service.name = "kidly-order" && span:kind = server }',
                'limit': 20,
            }],
            'range': {'from': 'START_MS', 'to': 'END_MS'},
        },
    }
    panes = quote(json.dumps(pane, separators=(',', ':')), safe='')
    panes = panes.replace('START_MS', '${__from}').replace('END_MS', '${__to}')
    print(json.dumps({
        'title': '관련 트레이스 보기',
        'url': '/explore?schemaVersion=1&panes=' + panes,
        'targetBlank': True,
    }, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
