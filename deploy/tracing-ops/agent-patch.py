#!/usr/bin/env python3
"""Read a Gateway or order Deployment JSON and print its tracing patch."""

import argparse
import json
import sys

NAMESPACE = 'kidly-dev'
AGENT_JAR = '/opt/kidly-tracing/opentelemetry-javaagent.jar'
HOST_AGENT_JAR = '/opt/app/kidly-tracing/opentelemetry-javaagent.jar'
AGENT_VOLUME = 'kidly-tracing-agent'
OTLP_ENDPOINT = 'http://tracing-alloy.kidly-dev.svc:4318/v1/traces'


def render_patch(deployment, java_options, rollback=False):
    metadata = deployment['metadata']
    service = metadata['name']
    if (deployment['kind'] != 'Deployment' or metadata['namespace'] != NAMESPACE
            or service not in ('gateway', 'order')):
        raise ValueError('expected the gateway or order Deployment in kidly-dev')

    pod = deployment['spec']['template']['spec']
    containers = [c for c in pod['containers'] if c['name'] == service]
    if len(containers) != 1:
        raise ValueError('expected one application container matching the Deployment')
    container = containers[0]
    env = container.get('env', [])
    if container.get('envFrom') != [{'secretRef': {'name': f'{service}-env'}}]:
        raise ValueError('expected the existing deploy-generated environment Secret reference')
    if not java_options.strip() or '-javaagent:' in java_options:
        raise ValueError('provide existing JVM options without an agent')
    settings = {
        'JAVA_TOOL_OPTIONS': f'{java_options} -javaagent:{AGENT_JAR}',
        'OTEL_SERVICE_NAME': f'kidly-{service}',
        'OTEL_RESOURCE_ATTRIBUTES': 'deployment.environment.name=dev',
        'OTEL_EXPORTER_OTLP_PROTOCOL': 'http/protobuf',
        'OTEL_EXPORTER_OTLP_TRACES_ENDPOINT': OTLP_ENDPOINT,
        'OTEL_METRICS_EXPORTER': 'none',
        'OTEL_LOGS_EXPORTER': 'none',
        'OTEL_PROPAGATORS': 'tracecontext',
        'OTEL_TRACES_SAMPLER': 'parentbased_traceidratio',
        'OTEL_TRACES_SAMPLER_ARG': '0.05',
        'OTEL_INSTRUMENTATION_KAFKA_ENABLED': 'false',
        'OTEL_INSTRUMENTATION_LOGBACK_MDC_ENABLED': 'false',
        'OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED': 'false',
    }
    mount = {'name': AGENT_VOLUME, 'mountPath': AGENT_JAR, 'readOnly': True}
    volume = {'name': AGENT_VOLUME, 'hostPath': {'path': HOST_AGENT_JAR, 'type': 'File'}}
    patches = [{'name': name, 'value': value} for name, value in settings.items()]
    existing_env = [entry for entry in env if entry['name'] in settings]
    existing_mounts = [entry for entry in container.get('volumeMounts', [])
                       if entry['name'] == AGENT_VOLUME or entry['mountPath'] == AGENT_JAR]
    existing_volumes = [entry for entry in pod.get('volumes', []) if entry['name'] == AGENT_VOLUME]
    if rollback:
        if (len(existing_env) != len(patches)
                or any(entry not in existing_env for entry in patches)
                or existing_mounts != [mount] or existing_volumes != [volume]):
            raise ValueError('live tracing settings do not match the generated deployment patch')
        patches = [{'name': name, '$patch': 'delete'} for name in settings]
        mount = {'mountPath': AGENT_JAR, '$patch': 'delete'}
        volume = {'name': AGENT_VOLUME, '$patch': 'delete'}
    elif existing_env or existing_mounts or existing_volumes or any(
            entry['name'].startswith('OTEL_') for entry in env):
        raise ValueError('existing inline JVM or tracing settings require review')

    return {
        'metadata': {'resourceVersion': metadata['resourceVersion']},
        'spec': {'template': {'spec': {
            'containers': [{
                'name': service,
                'env': patches,
                'volumeMounts': [mount],
            }],
            'volumes': [volume],
        }}},
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-tool-options', required=True, help='Current non-secret JVM flags')
    parser.add_argument('--rollback', action='store_true', help='Remove only the matching tracing patch')
    args = parser.parse_args()
    try:
        print(json.dumps(render_patch(json.load(sys.stdin), args.java_tool_options, args.rollback), indent=2))
    except (KeyError, TypeError, ValueError) as error:
        sys.exit(f'Cannot generate tracing patch: {error}')
