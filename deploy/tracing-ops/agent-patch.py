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


def render_patch(deployment, java_options):
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
    if any(entry['name'] == 'JAVA_TOOL_OPTIONS' or entry['name'].startswith('OTEL_') for entry in env):
        raise ValueError('existing inline JVM or tracing settings require review')
    if not java_options.strip() or '-javaagent:' in java_options:
        raise ValueError('provide existing JVM options without an agent')
    if any(volume['name'] == AGENT_VOLUME for volume in pod.get('volumes', [])):
        raise ValueError('agent volume already exists')
    if any(mount['name'] == AGENT_VOLUME or mount['mountPath'] == AGENT_JAR
           for mount in container.get('volumeMounts', [])):
        raise ValueError('agent mount already exists')

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
    return {
        'spec': {'template': {'spec': {
            'containers': [{
                'name': service,
                'env': [{'name': name, 'value': value} for name, value in settings.items()],
                'volumeMounts': [{'name': AGENT_VOLUME, 'mountPath': AGENT_JAR, 'readOnly': True}],
            }],
            'volumes': [{'name': AGENT_VOLUME, 'hostPath': {'path': HOST_AGENT_JAR, 'type': 'File'}}],
        }}},
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-tool-options', required=True, help='Current non-secret JVM flags')
    args = parser.parse_args()
    try:
        print(json.dumps(render_patch(json.load(sys.stdin), args.java_tool_options), indent=2))
    except (KeyError, TypeError, ValueError) as error:
        sys.exit(f'Cannot generate tracing patch: {error}')
