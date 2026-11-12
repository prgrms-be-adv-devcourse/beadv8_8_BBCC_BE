"""Render a strategic merge patch for a disposable tracing test Deployment."""
import argparse
import json

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("service", choices=["gateway", "order"])
parser.add_argument("--full-sampling", action="store_true")
args = parser.parse_args()
jar = "/opt/kidly-tracing/opentelemetry-javaagent.jar"
settings = {
    "JAVA_TOOL_OPTIONS": f"-Xmx256m -javaagent:{jar}",
    "OTEL_SERVICE_NAME": f"kidly-{args.service}",
    "OTEL_RESOURCE_ATTRIBUTES": "deployment.environment.name=tracing-trial",
    "OTEL_EXPORTER_OTLP_PROTOCOL": "http/protobuf",
    "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT": "http://alloy-trial:4318/v1/traces",
    "OTEL_METRICS_EXPORTER": "none",
    "OTEL_LOGS_EXPORTER": "none",
    "OTEL_PROPAGATORS": "tracecontext",
    "OTEL_TRACES_SAMPLER": "parentbased_traceidratio",
    "OTEL_TRACES_SAMPLER_ARG": "1" if args.full_sampling else "0.05",
    "OTEL_INSTRUMENTATION_KAFKA_ENABLED": "false",
    "OTEL_INSTRUMENTATION_LOGBACK_MDC_ENABLED": "false",
    "OTEL_INSTRUMENTATION_LOGBACK_APPENDER_ENABLED": "false",
}
container = {
    "name": args.service,
    "env": [{"name": name, "value": value} for name, value in settings.items()],
    "volumeMounts": [{"name": "otel-agent", "mountPath": jar, "readOnly": True}],
}
volume = {"name": "otel-agent", "hostPath": {"path": jar, "type": "File"}}
print(json.dumps({
    "metadata": {"name": args.service, "namespace": "kidly-tracing-trial"},
    "spec": {"template": {"spec": {"containers": [container], "volumes": [volume]}}},
}, indent=2))
