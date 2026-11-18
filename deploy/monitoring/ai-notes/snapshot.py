"""Fixed local metric reader; no raw logs, arbitrary PromQL or original labels."""
import json
import math
import re
import time
import urllib.parse
import urllib.request
import uuid

from contracts import AnalysisContext, Evidence

JOBS = ('kidly-gateway', 'kidly-search', 'kidly-recommend', 'kidly-member', 'kidly-payment',
        'kidly-product', 'kidly-order', 'kidly-community', 'kidly-chatbot', 'kidly-settlement', 'kidly-notification')
MATCH = '|'.join(JOBS)
HTTP_WINDOW = 1800
HTTP_COMPARISON = ('requests_30m', 'errors_30m', 'duration_sum_30m')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError('redirect refused')


# 응답 시간(평균)을 믿을 수 없는 서비스. 요청 수 · 5xx 는 그대로 쓴다
NO_DURATION = 'kidly-gateway'
# ERROR 로그의 예외 종류(error.type)별 건수. 로그 원문 · 메시지는 읽지 않고 Loki 가 집계한 숫자와 클래스 이름만 쓴다
EXCEPTION_TOP = 8
EXCEPTION_NAME = re.compile(r'[A-Za-z_][A-Za-z0-9_]{0,80}')
# 예외 스택에서 처음 지나간 우리 코드(com.bbcc.kidly) 위치. 프록시(<generated>) · 프레임워크 줄은 건너뛴다
SITE_TOP = 5
SITE_PATTERN = ('at com[.]bbcc[.]kidly[.](?:[A-Za-z0-9_]+[.])*'
                '(?P<site>[A-Z][A-Za-z0-9_]*[.][A-Za-z0-9_]+[(][A-Za-z0-9_]+[.]java:[0-9]+[)])')
SITE = re.compile(r'([A-Z][A-Za-z0-9_]{0,80})\.([A-Za-z_][A-Za-z0-9_]{0,80})\([A-Za-z0-9_]+\.java:([0-9]{1,6})\)')
# 비어 있으면 '없음'이 정상인 지표(빠진 지표 목록에 넣지 않는다)
EMPTY_IS_NONE = ('restarted_minutes_ago', 'business_stuck_items', 'business_outbox_unsent', 'business_outbox_blocked',
                 'business_outbox_oldest_seconds', 'business_dlt_open', 'business_dlt_new_30m')
# 업무 적체(멈춘 주문 · 결제, outbox, DLT). 0 은 근거로 보내지 않는다(값이 있는 서비스만 근거)
BUSINESS = 'business_'
BUSINESS_NOTE = ('business_*: business backlog per service (stuck orders/charges, outbox unsent/blocked/oldest age, open DLT, '
                 'new payment DLT); only nonzero values are sent. business_dlt_new_30m is the number of payment messages newly sent to DLT '
                 'in 30 minutes (payment has no DLT reprocessing, so no open count). '
                 'Absence means none observed or not collected, not proof of health.')


def opener():
    return urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect)


def business(aggregate, app, *metrics):
    """서비스마다 자기 지표 하나라 job 별로 모아 or 로 잇는다. 1분 안에 수집된 값만 쓴다(다른 지표와 같은 신선도 조건)."""
    parts = []
    for metric in metrics:
        name, _, extra = metric.partition('{')
        selector = name + '{' + app + (',' + extra.rstrip('}') if extra else '') + '}'
        parts.append(f'{aggregate} by (job) ({selector} and (time()-timestamp({selector})<60))')
    return ' or '.join(parts)


def queries(env):
    if env not in ('dev', 'prod'):
        raise ValueError('unsupported environment')
    app = 'env="' + env + '",job=~"' + MATCH + '"'
    # 대시보드와 같이 수집 요청(/actuator)과 알림 스트림(SSE, 연결이 오래 열려 평균을 왜곡)을 뺀다
    http = app + ',uri!~"/actuator.*|/api/v1/(child/)?notifications/stream"'
    lag = 'env="' + env + '",job="kidly-kafka",consumergroup=~"kidly[.][a-z]+"'
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
        # Gateway 는 하위 서비스로 넘긴 요청이 모두 uri="UNKNOWN" 이라 SSE 를 걸러낼 수 없어 응답 시간을 보내지 않는다
        'duration_sum_30m': ('seconds', f'sum by (job) (increase(http_server_requests_seconds_sum{{{http},job!~"{NO_DURATION}"}}[30m]))'),
        'hikari_pending': ('count', f'sum by (job) (hikaricp_connections_pending{{{app}}} and (time()-timestamp(hikaricp_connections_pending{{{app}}})<60))'),
        'hikari_utilization': ('ratio', f'max by (job) (hikaricp_connections_active{{{app}}}/hikaricp_connections_max{{{app}}} and (time()-timestamp(hikaricp_connections_active{{{app}}})<60))'),
        'kafka_lag': ('count', f'sum(kafka_consumergroup_lag{{env="{env}"}} and (time()-timestamp(kafka_consumergroup_lag{{env="{env}"}})<60))'),
        # 서비스별로 볼 수 있게 한다: ERROR 로그 수, 30분 안에 새로 뜬 프로세스 수(배포 · 장애 · 수동 패치), 구독 그룹 kidly.<도메인>의 미처리
        'error_logs_30m': ('estimated_count', f'sum by (job) (increase(logback_events_total{{{app},level="error"}}[30m]))'),
        'restarts_30m': ('count', f'count by (job) ((time() - process_start_time_seconds{{{app}}}) < 1800) or (0 * max by (job) (process_start_time_seconds{{{app}}}))'),
        # 30분 안에 새로 뜬 서비스만, 가장 최근 시작이 몇 분 전인지(오류 · 지연이 바뀐 시점과 맞춰 보게 한다)
        'restarted_minutes_ago': ('minutes', f'min by (job) ((time() - process_start_time_seconds{{{app}}}) / 60) < 30'),
        # member 서비스는 auth · childprofile · member · seller 그룹을 함께 쓴다(대시보드 상태판과 같은 규칙)
        'kafka_lag_by_service': ('count', f'sum by (job) (label_replace(label_replace((kafka_consumergroup_lag{{{lag}}} >= 0) and (time()-timestamp(kafka_consumergroup_lag{{{lag}}})<60), "job", "kidly-$1", "consumergroup", "kidly[.]([a-z]+)"), "job", "kidly-member", "consumergroup", "kidly[.](auth|childprofile|member|seller)"))'),
        # 업무 적체: 서비스별 합(최고령은 최댓값). 멈춘 주문은 kind, 멈춘 결제는 status 별 값을 더한다
        'business_stuck_items': ('count', business('sum', app, 'kidly_order_stuck_orders', 'kidly_payment_charges_stuck')),
        'business_outbox_unsent': ('count', business(
            'sum', app, 'kidly_order_outbox_unsent_messages', 'kidly_community_outbox_messages{state="pending"}',
            'kidly_payment_outbox_unsent_messages', 'kidly_notification_unsent', 'settlement_outbox_unsent')),
        'business_outbox_blocked': ('count', business(
            'sum', app, 'kidly_order_outbox_quarantined_messages', 'kidly_community_outbox_messages{state="quarantined"}',
            'kidly_payment_outbox_parked_messages')),
        'business_outbox_oldest_seconds': ('seconds', business(
            'max', app, 'kidly_order_outbox_oldest_unsent_age_seconds', 'kidly_community_outbox_oldest_age_seconds',
            'kidly_payment_outbox_oldest_unsent_age_seconds', 'kidly_notification_oldest_unsent_age_seconds')),
        'business_dlt_open': ('count', business(
            'sum', app, 'kidly_order_dlt_messages{state="open"}', 'kidly_community_dlt_messages{state="open"}')),
        # 결제 DLT 는 재처리 장치가 없어 열린 건수가 없다. 누적 counter 라 30분 증가량(새로 DLT 로 간 수)을 쓴다
        'business_dlt_new_30m': ('estimated_count', f'sum by (job) (increase(kidly_payment_dlt_messages_total{{{app}}}[30m]))'),
        'host_memory_available': ('percent', 'min(100*node_memory_MemAvailable_bytes/node_memory_MemTotal_bytes and (time()-timestamp(node_memory_MemAvailable_bytes)<60))'),
        'host_disk_used': ('percent', 'max(100*(1-node_filesystem_avail_bytes{mountpoint="/",fstype!~"tmpfs|overlay"}/node_filesystem_size_bytes{mountpoint="/",fstype!~"tmpfs|overlay"}) and (time()-timestamp(node_filesystem_avail_bytes{mountpoint="/",fstype!~"tmpfs|overlay"})<60))'),
    }


def metric_rows(expression, at):
    params = urllib.parse.urlencode({'query': expression, 'time': at, 'timeout': '3s'})
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
    return rows


def log_rows(expression, at):
    params = urllib.parse.urlencode({'query': expression, 'time': at})
    with opener().open('http://127.0.0.1:31100/loki/api/v1/query?' + params, timeout=5) as response:
        raw = response.read(256*1024+1)
    if len(raw) > 256*1024:
        raise ValueError('oversized log response')
    result = json.loads(raw)
    if result.get('status') != 'success' or result.get('data', {}).get('resultType') != 'vector':
        raise ValueError('incomplete log response')
    rows = result['data']['result']
    if not isinstance(rows, list) or len(rows) > EXCEPTION_TOP:
        raise ValueError('too many log rows')
    return rows


def exception_query(environment):
    if environment not in ('dev', 'prod'):
        raise ValueError('unsupported environment')
    selector = '{env="' + environment + '", service_name=~"' + MATCH + '", level="ERROR"}'
    return (f'topk({EXCEPTION_TOP}, sum by (service_name, error_type) (count_over_time({selector} '
            '| json error_type="error.type" | __error__="" | error_type != "" [30m])))')


def exception_facts(environment, now):
    counts = {}
    for row in log_rows(exception_query(environment), now):
        service = row['metric'].get('service_name')
        # 패키지 경로를 빼고 클래스 이름만 남긴다(내부 클래스 $ 이하도 뺀다). 형식이 다르면 버린다
        name = str(row['metric'].get('error_type', '')).rsplit('.', 1)[-1].split('$', 1)[0]
        value = float(row['value'][1])
        if service in JOBS and EXCEPTION_NAME.fullmatch(name) and math.isfinite(value) and value > 0:
            # 이름을 줄이면 다른 클래스가 같은 이름이 될 수 있어 서비스 · 이름별로 더한다(근거 중복 방지)
            counts[service, name] = counts.get((service, name), 0) + value
    for (service, name), value in counts.items():
        yield 'error_type_30m:' + name, value, service


def site_query(environment):
    if environment not in ('dev', 'prod'):
        raise ValueError('unsupported environment')
    selector = '{env="' + environment + '", service_name=~"' + MATCH + '", level="ERROR"}'
    return (f'topk({SITE_TOP}, sum by (service_name, error_type, site) (count_over_time({selector} '
            '| json error_type="error.type", stack="error.stack_trace" | __error__="" | error_type != "" '
            '| line_format "{{.stack}}" | regexp "' + SITE_PATTERN + '" | site != "" [30m])))')


def site_facts(environment, now):
    counts = {}
    for row in log_rows(site_query(environment), now):
        service = row['metric'].get('service_name')
        name = str(row['metric'].get('error_type', '')).rsplit('.', 1)[-1].split('$', 1)[0]
        site = SITE.fullmatch(str(row['metric'].get('site', '')))
        value = float(row['value'][1])
        where = ':'.join((name,) + site.groups()) if site else ''
        # 근거 지표 이름 상한(200자) 안에 들지 않으면 버린다
        if service in JOBS and EXCEPTION_NAME.fullmatch(name) and site and len(where) <= 180 and math.isfinite(value) and value > 0:
            key = service, where
            counts[key] = counts.get(key, 0) + value
    for (service, where), value in counts.items():
        yield 'error_site_30m:' + where, value, service


def previous_http_facts(environment, now, facts):
    current = {(item.metric, item.service): item.value for item in facts}
    previous = {}
    for name in HTTP_COMPARISON:
        _, expression = queries(environment)[name]
        seen = set()
        for row in metric_rows(expression, now - HTTP_WINDOW):
            service = row['metric'].get('job')
            if service not in JOBS:
                continue
            if service in seen:
                raise ValueError('ambiguous comparison aggregate')
            seen.add(service)
            value = float(row['value'][1])
            if math.isfinite(value) and value >= 0:
                previous[name, service] = value

    for service in JOBS:
        count = current.get(('requests_30m', service))
        prior_count = previous.get(('requests_30m', service))
        if count is None or prior_count is None or min(count, prior_count) <= 0:
            continue
        yield ('requests_previous_30m', prior_count, 'estimated_count', service)
        errors = previous.get(('errors_30m', service))
        if errors is not None and ('errors_30m', service) in current:
            yield ('errors_previous_30m', errors, 'estimated_count', service)
        if service == NO_DURATION:
            continue
        duration = current.get(('duration_sum_30m', service))
        prior_duration = previous.get(('duration_sum_30m', service))
        if duration is not None and prior_duration is not None:
            yield ('mean_response_current_30m', round(1000 * (duration / count), 3), 'milliseconds', service)
            yield ('mean_response_previous_30m', round(1000 * (prior_duration / prior_count), 3), 'milliseconds', service)


def collect(environment, now=None, *, compare_http=False):
    now = int(time.time()) if now is None else now
    facts, limitations, missing = [], [], []
    for name, (unit, expression) in queries(environment).items():
        rows = metric_rows(expression, now)
        seen = set()
        accepted = 0
        for row in rows:
            service = row['metric'].get('app_job') if name == 'app_expected' else row['metric'].get('job')
            per_service = name in ('app_expected', 'up', 'requests_30m', 'errors_30m', 'duration_sum_30m', 'hikari_pending', 'hikari_utilization',
                                   'error_logs_30m', 'restarts_30m', 'kafka_lag_by_service', 'restarted_minutes_ago') \
                or name.startswith(BUSINESS)
            if per_service and service not in JOBS:
                continue
            if name == 'duration_sum_30m' and service == NO_DURATION:
                continue
            if not per_service:
                service = None
            value = float(row['value'][1])
            if not math.isfinite(value) or value < 0 or (unit == 'boolean' and value not in (0, 1)):
                continue
            if name.startswith(BUSINESS) and value == 0:
                continue
            if service in seen:
                raise ValueError('ambiguous aggregate')
            seen.add(service)
            if unit == 'minutes':
                value = round(value, 1)
            scope = 'global' if name.startswith('host_') else environment
            facts.append(Evidence('f'+str(len(facts)), name, value, unit, scope, service))
            accepted += 1
        if not accepted and name not in EMPTY_IS_NONE:
            missing.append(name)
    # 지표마다 한 줄씩 쓰면 계약의 제약 20개를 넘는다. 빠진 지표는 한 줄로 묶는다
    if missing:
        limitations.append('No finite fresh/available result for: ' + ', '.join(missing) + '; absence is not healthy zero.')
    limitations.extend((
        'HTTP counts/duration are 30-minute estimates; mean is not p95. Low traffic limits interpretation.',
        'HTTP facts exclude /actuator and notification stream (SSE) paths. kidly-gateway has no duration: its proxied requests are uri=UNKNOWN, so long-lived SSE streams cannot be excluded.',
        'Absent 5xx series alone can mean no 5xx observed. Never add Gateway and service requests together.',
        'Check environment_enabled/environment_transition/app_expected before interpreting missing or down apps; disabled means intentional stop.',
        'Host metrics are worst observed across available hosts, not a specific service host; absent hosts can be omitted.',
        'Kafka lag is one snapshot; it does not establish increasing lag.',
        # 아래 두 줄은 MCP 전용 지표 설명이다. Groq 경로는 지표와 함께 이 줄도 뺀다(groq_provider.MCP_ONLY_METRICS)
        'kafka_lag_by_service maps consumer group kidly.<name> to kidly-<name>; kidly-member sums auth, childprofile, member and seller groups. DLT groups and unknown offsets are excluded.',
        'error_logs_30m counts ERROR log events, not distinct incidents. restarts_30m counts process instances started within 30 minutes (deploy, crash or manual patch); it does not say which, and several instances count separately.',
    ))
    try:
        exceptions = list(exception_facts(environment, now))
    except (OSError, ValueError, KeyError, TypeError, IndexError):
        exceptions = None
    for metric, value, service in exceptions or ():
        facts.append(Evidence('f' + str(len(facts)), metric, value, 'estimated_count', environment, service))
    limitations.append(
        'ERROR log exception types were unavailable; this is not zero.' if exceptions is None else
        'error_type_30m:<Class> counts ERROR log events per service by exception class (top 8, 30 minutes). '
        'Messages are not included; counts are events, not distinct incidents or failed requests.' if exceptions else
        'No ERROR log with an exception type in 30 minutes; ERROR logs without an exception type are not counted.'
    )
    try:
        sites = list(site_facts(environment, now))
    except (OSError, ValueError, KeyError, TypeError, IndexError):
        sites = None
    for metric, value, service in sites or ():
        facts.append(Evidence('f' + str(len(facts)), metric, value, 'estimated_count', environment, service))
    if sites:
        limitations.append(
            'error_site_30m:<Exception>:<Class>:<method>:<line> counts ERROR events by the first own-code frame (com.bbcc.kidly) '
            'in the stack (top 5, 30 minutes). It is where our code was when it failed, not necessarily the root cause.')
    if any(fact.metric == 'restarted_minutes_ago' for fact in facts):
        limitations.append(
            'restarted_minutes_ago: minutes since the newest process start, only for services started within 30 minutes '
            '(deploy, crash or manual; not which). Timing alignment is not causation.')
    if any(fact.metric.startswith(BUSINESS) for fact in facts):
        limitations.append(BUSINESS_NOTE)
    if compare_http:
        try:
            comparison = list(previous_http_facts(environment, now, facts))
        except (OSError, ValueError, KeyError, TypeError, IndexError):
            comparison = []
        for metric, value, unit, service in comparison:
            if math.isfinite(value):
                facts.append(Evidence('f' + str(len(facts)), metric, value, unit, environment, service))
        limitations.append(
            'HTTP comparison pairs the observation window with the immediately preceding 30 minutes. '
            'Only services with nonzero measured requests in both windows are paired; a missing pair is not zero.'
            if comparison else 'No comparable previous HTTP window was available.'
        )
    return AnalysisContext(1, str(uuid.uuid4()), now-1800, now, (environment,), tuple(facts), tuple(limitations))
