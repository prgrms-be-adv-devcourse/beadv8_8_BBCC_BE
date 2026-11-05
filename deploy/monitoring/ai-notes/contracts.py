"""Provider-independent contracts. No credentials, model calls or deployment side effects."""
from dataclasses import dataclass
import math
import time
from typing import Callable, Protocol, Tuple

VERSION = 1


@dataclass(frozen=True)
class Evidence:
    evidence_id: str
    metric: str
    value: float
    unit: str


@dataclass(frozen=True)
class AnalysisContext:
    version: int
    context_id: str
    window_start: int
    window_end: int
    environments: Tuple[str, ...]
    evidence: Tuple[Evidence, ...]
    limitations: Tuple[str, ...]  # Missing metrics, stopped services, traffic limits, etc.


@dataclass(frozen=True)
class Finding:
    observation: str
    hypothesis: str
    checks: Tuple[str, ...]
    recommendation: str
    evidence_ids: Tuple[str, ...]


@dataclass(frozen=True)
class AnalysisReport:
    version: int
    context_id: str
    provider: str
    model: str
    generated_at: int
    findings: Tuple[Finding, ...]
    limitations: Tuple[str, ...]


class AnalysisProvider(Protocol):
    def analyze(self, context: AnalysisContext) -> AnalysisReport:
        """Actually infer using an AI, then normalize its response to the common contract."""
        ...


class NotePublisher(Protocol):
    def publish(self, context: AnalysisContext, report: AnalysisReport) -> str:
        """Store an analysis note only; return a receipt. Never operate services."""
        ...


def text(value, maximum=1000):
    if not isinstance(value, str) or not 1 <= len(value) <= maximum:
        raise ValueError('invalid text')


def validate_context(context, now):
    if context.version != VERSION:
        raise ValueError('unsupported context version')
    text(context.context_id, 100)
    if type(context.window_start) is not int or type(context.window_end) is not int:
        raise ValueError('observation timestamps must be integer seconds')
    if not 0 <= context.window_start < context.window_end <= now + 60:
        raise ValueError('invalid observation window')
    if context.window_end - context.window_start > 3600 or now - context.window_end > 3600:
        raise ValueError('observation window too long or stale')
    if not context.environments or set(context.environments) - {'dev', 'prod'}:
        raise ValueError('unsupported environment')
    if len(context.evidence) > 200 or len(context.limitations) > 20:
        raise ValueError('context too large')
    ids = set()
    for item in context.evidence:
        text(item.evidence_id, 100)
        text(item.metric, 200)
        text(item.unit, 50)
        if item.evidence_id in ids or isinstance(item.value, bool) or not isinstance(item.value, (float, int)) or not math.isfinite(item.value):
            raise ValueError('duplicate evidence or nonfinite value')
        ids.add(item.evidence_id)
    for limitation in context.limitations:
        text(limitation)


def validate_report(context, report, now):
    if report.version != VERSION or report.context_id != context.context_id:
        raise ValueError('report version or context mismatch')
    if type(report.generated_at) is not int:
        raise ValueError('generation timestamp must be integer seconds')
    if not context.window_end <= report.generated_at <= now + 60:
        raise ValueError('invalid generation timestamp')
    text(report.provider, 100)
    text(report.model, 100)
    if len(report.findings) > 5 or len(report.limitations) > 20:
        raise ValueError('report too large')
    if not report.findings and not report.limitations:
        raise ValueError('empty result must explain its limits')
    ids = {item.evidence_id for item in context.evidence}
    for finding in report.findings:
        for value in (finding.observation, finding.hypothesis, finding.recommendation):
            text(value)
        if not 1 <= len(finding.checks) <= 5 or not 1 <= len(finding.evidence_ids) <= 10:
            raise ValueError('checks and evidence required')
        for check in finding.checks:
            text(check)
        if any(ref not in ids for ref in finding.evidence_ids):
            raise ValueError('unknown evidence reference')
    for limitation in report.limitations:
        text(limitation)


def publish_analysis(context: AnalysisContext, report: AnalysisReport, publisher: NotePublisher, now: float) -> str:
    """Shared by a server worker and an external MCP AI client's submitted report."""
    validate_context(context, now)
    validate_report(context, report, now)
    return publisher.publish(context, report)


def analyze_and_publish(context: AnalysisContext, provider: AnalysisProvider, publisher: NotePublisher,
                        clock: Callable[[], float] = time.time) -> str:
    """API/local-model worker entrypoint. MCP-hosted AI inference happens in its client."""
    validate_context(context, clock())
    report = provider.analyze(context)
    return publish_analysis(context, report, publisher, clock())
