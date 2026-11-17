"""Shared Java Agent settings for the tracing pilot."""

NAMESPACE = 'kidly-dev'
SERVICES = ('gateway', 'order')
AGENT_JAR = '/opt/kidly-tracing/opentelemetry-javaagent.jar'
HOST_AGENT_JAR = '/opt/app/kidly-tracing/opentelemetry-javaagent.jar'
AGENT_VOLUME = 'kidly-tracing-agent'
OTLP_ENDPOINT = 'http://tracing-alloy.kidly-dev.svc:4318/v1/traces'


def agent_settings(service, java_options):
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
    return [{'name': name, 'value': value} for name, value in settings.items()], mount, volume
