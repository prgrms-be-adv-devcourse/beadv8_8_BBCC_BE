"""Fixed local metric reader; no raw logs, arbitrary PromQL or original labels."""
import json
import math
import time
import urllib.parse
import urllib.request
import uuid

from contracts import AnalysisContext, Evidence

JOBS = ('kidly-gateway', 'kidly-search', 'kidly-recommend', 'kidly-member', 'kidly-payment',
        'kidly-product', 'kidly-order', 'kidly-community', 'kidly-settlement', 'kidly-notification')
MATCH = '|'.join(JOBS)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError('redirect refused')


def opener():
    return urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect)


def queries(env):
    if env not in ('dev', 'prod'):
        raise ValueError('unsupported environment')
    app = 'env="' + env + '",job=~"' + MATCH + '"'
    http = app + ',uri!~"/actuator.*"'
    return {
        'environment_transition': ('boolean', f'max(kidly_environment_transition_active{{env="{env}"}} and (time()-timestamp(kidly_environment_transition_active{{env="{env}"}})<60))'),
        'transition_age': ('seconds', f'max((time()-kidly_environment_changed_timestamp_seconds{{env="{env}"}}) and (time()-timestamp(kidly_environment_changed_timestamp_seconds{{env="{env}"}})<60))'),
        'host_cpu_used': ('percent', 'max(100*(1-avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m]))) and on(instance) (max by(instance)(time()-timestamp(node_cpu_seconds_total{mode="idle"}))<60))'),
        'host_oom_30m': ('estimated_count', 'sum(increase(node_vmstat_oom_kill[30m]) and (time()-timestamp(node_vmstat_oom_kill)<60))'),
        'environment_enabled': ('boolean', f'max(kidly_environment_enabled{{env="{env}"}} and (time()-timestamp(kidly_environment_enabled{{env="{env}"}})<60))'),
        'app_expected': ('boolean', f'max by (app_job) (kidly_app_expected{{env="{env}"}} and (time()-timestamp(kidly_app_expected{{env="{env}"}})<60))'),
        'up': ('boolean', f'min by (job) (up{{{app}}} and (time()-timestamp(up{{{app}}})<60))'),
        'requests_30m': ('estimated_count', f'sum by (job) (increase(http_server_requests_seconds_count{{{http}}}[30m]))'),
        'errors_30m': ('estimated_count', f'sum by (job) (increase(http_server_requests_seconds_count{{{http},status=~"5.."}}[30m]))'),
        'duration_sum_30m': ('seconds', f'sum by (job) (increase(http_server_requests_seconds_sum{{{http}}}[30m]))'),
        'hikari_pending': ('count', f'sum by (job) (hikaricp_connections_pending{{{app}}} and (time()-timestamp(hikaricp_connections_pending{{{app}}})<60))'),
        'hikari_utilization': ('ratio', f'max by (job) (hikaricp_connections_active{{{app}}}/hikaricp_connections_max{{{app}}} and (time()-timestamp(hikaricp_connections_active{{{app}}})<60))'),
        'kafka_lag': ('count', f'sum(kafka_consumergroup_lag{{env="{env}"}} and (time()-timestamp(kafka_consumergroup_lag{{env="{env}"}})<60))'),
        'host_memory_available': ('percent', 'min(100*node_memory_MemAvailable_bytes/node_memory_MemTotal_bytes and (time()-timestamp(node_memory_MemAvailable_bytes)<60))'),
        'host_disk_used': ('percent', 'max(100*(1-node_filesystem_avail_bytes{mountpoint="/",fstype!~"tmpfs|overlay"}/node_filesystem_size_bytes{mountpoint="/",fstype!~"tmpfs|overlay"}) and (time()-timestamp(node_filesystem_avail_bytes{mountpoint="/",fstype!~"tmpfs|overlay"})<60))'),
    }


def collect(environment, now=None):
    now = int(time.time()) if now is None else now
    facts, limitations = [], []
    for name, (unit, expression) in queries(environment).items():
        params = urllib.parse.urlencode({'query': expression, 'time': now, 'timeout': '3s'})
        with opener().open('http://127.0.0.1:30090/api/v1/query?' + params, timeout=5) as response:
            raw = response.read(1024*1024+1)
        if len(raw) > 1024*1024:
            raise ValueError('oversized metric response')
        result = json.loads(raw)
        if result.get('status') != 'success' or result.get('warnings') or result.get('data', {}).get('resultType') != 'vector':
            raise ValueError('incomplete metric response')
        rows = result['data']['result']
        if not isinstance(rows, list) or len(rows) > 30:
            raise ValueError('too many metric rows')
        seen = set()
        accepted = 0
        for row in rows:
            service = row['metric'].get('app_job') if name == 'app_expected' else row['metric'].get('job')
            per_service = name in ('app_expected', 'up', 'requests_30m', 'errors_30m', 'duration_sum_30m', 'hikari_pending', 'hikari_utilization')
            if per_service and service not in JOBS:
                continue
            if not per_service:
                service = None
            value = float(row['value'][1])
            if not math.isfinite(value) or value < 0 or (unit == 'boolean' and value not in (0, 1)):
                continue
            if service in seen:
                raise ValueError('ambiguous aggregate')
            seen.add(service)
            scope = 'global' if name.startswith('host_') else environment
            facts.append(Evidence('f'+str(len(facts)), name, value, unit, scope, service))
            accepted += 1
        if not accepted:
            limitations.append(name + ': no finite fresh/available result; absence is not healthy zero')
    limitations.extend((
        'HTTP counts/duration are 30-minute estimates; mean is not p95. Low traffic limits interpretation.',
        'Absent 5xx series alone can mean no 5xx observed. Never add Gateway and service requests together.',
        'Check environment_enabled/environment_transition/app_expected before interpreting missing or down apps; disabled means intentional stop.',
        'Host metrics are worst observed across available hosts, not a specific service host; absent hosts can be omitted.',
        'Kafka lag is one aggregate snapshot; it does not establish increasing lag or identify a group.',
    ))
    return AnalysisContext(1, str(uuid.uuid4()), now-1800, now, (environment,), tuple(facts), tuple(limitations))
