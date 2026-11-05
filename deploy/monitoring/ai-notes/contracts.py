"""Provider-independent contracts; adapters own authentication, storage and transport."""
from dataclasses import asdict, dataclass
import hashlib
import json
import math
import re
import time
from typing import Callable, Optional, Protocol, Tuple

VERSION = 1
MAX_ANALYSIS_SECONDS = 300


@dataclass(frozen=True)
class Evidence:
    evidence_id: str
    metric: str
    value: float
    unit: str
    environment: str
    service: Optional[str] = None


@dataclass(frozen=True)
class AnalysisContext:
    version: int
    context_id: str
    window_start: int
    window_end: int
    environments: Tuple[str, ...]
    evidence: Tuple[Evidence, ...]
    limitations: Tuple[str, ...]


@dataclass(frozen=True)
class AnalysisTicket:
    context: AnalysisContext
    started_at: int  # Stamped by trusted coordinator, never the submitted report.

    @property
    def deadline(self) -> int:
        return self.started_at + MAX_ANALYSIS_SECONDS


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
    provider: str  # Self-reported attribution; not authentication.
    model: str
    generated_at: int
    findings: Tuple[Finding, ...]
    limitations: Tuple[str, ...]


@dataclass(frozen=True)
class Publication:
    context: AnalysisContext
    report: AnalysisReport
    source: str  # Assigned by authenticated entrypoint, not AI input.
    principal: str
    published_at: int
    stale_at_publish: bool
    content_digest: str


class AnalysisProvider(Protocol):
    def analyze(self, context: AnalysisContext, deadline: int) -> AnalysisReport:
        """Infer and normalize; adapter must honor deadline and cancel/timeout its call."""
        ...


class ContextStore(Protocol):
    def get(self, context_id: str) -> AnalysisTicket:
        """Return coordinator-issued immutable context; absent IDs raise KeyError."""
        ...


class NotePublisher(Protocol):
    def publish(self, publication: Publication) -> str:
        """Durable receipt, idempotent per context_id + digest; conflicting content fails.

        Render strings as plain text/escape HTML. Display original context limitations,
        observed/published times, staleness and authenticated source. Never operate apps.
        """
        ...


def text(value, maximum=1000):
    if not isinstance(value, str) or not 1 <= len(value) <= maximum:
        raise ValueError('invalid text')
    if any((ord(char) < 32 and char not in '\n\t') or 127 <= ord(char) < 160 for char in value):
        raise ValueError('control characters forbidden')


def collection(value, maximum):
    if type(value) is not tuple or len(value) > maximum:
        raise ValueError('bounded tuple required')


def version(value):
    if type(value) is not int or value != VERSION:
        raise ValueError('unsupported version')


def validate_context(context: AnalysisContext, now: float):
    if not isinstance(context, AnalysisContext):
        raise ValueError('context required')
    version(context.version)
    text(context.context_id, 100)
    if type(context.window_start) is not int or type(context.window_end) is not int:
        raise ValueError('integer observation timestamps required')
    if not 0 <= context.window_start < context.window_end <= now + 60:
        raise ValueError('invalid observation window')
    if context.window_end - context.window_start > 3600 or now - context.window_end > 3600:
        raise ValueError('observation window too long or stale at start')
    collection(context.environments, 2)
    if not context.environments or any(env not in ('dev', 'prod') for env in context.environments) or len(set(context.environments)) != len(context.environments):
        raise ValueError('unsupported environment')
    collection(context.evidence, 200)
    collection(context.limitations, 20)
    ids = set()
    for item in context.evidence:
        if not isinstance(item, Evidence):
            raise ValueError('evidence required')
        text(item.evidence_id, 100)
        text(item.metric, 200)
        text(item.unit, 50)
        if not re.fullmatch(r'[a-zA-Z_:][a-zA-Z0-9_:]*', item.metric):
            raise ValueError('invalid metric name')
        if item.environment not in context.environments + ('global',):
            raise ValueError('evidence environment outside scope')
        if item.service is not None and (item.environment == 'global' or not isinstance(item.service, str) or not re.fullmatch(r'kidly-[a-z][a-z0-9-]{0,50}', item.service)):
            raise ValueError('invalid evidence service')
        if item.evidence_id in ids or isinstance(item.value, bool) or not isinstance(item.value, (float, int)) or not math.isfinite(item.value):
            raise ValueError('duplicate evidence or nonfinite value')
        ids.add(item.evidence_id)
    for limitation in context.limitations:
        text(limitation)


def validate_report(context: AnalysisContext, report: AnalysisReport, now: float):
    if not isinstance(report, AnalysisReport):
        raise ValueError('report required')
    version(report.version)
    if report.context_id != context.context_id:
        raise ValueError('context mismatch')
    if type(report.generated_at) is not int or not context.window_end <= report.generated_at <= now + 60:
        raise ValueError('invalid generation timestamp')
    text(report.provider, 100)
    text(report.model, 100)
    collection(report.findings, 5)
    collection(report.limitations, 20)
    if not report.findings and not report.limitations:
        raise ValueError('empty result must explain its limits')
    ids = {item.evidence_id for item in context.evidence}
    for finding in report.findings:
        if not isinstance(finding, Finding):
            raise ValueError('finding required')
        for value in (finding.observation, finding.hypothesis, finding.recommendation):
            text(value)
        collection(finding.checks, 5)
        collection(finding.evidence_ids, 10)
        if not finding.checks or not finding.evidence_ids:
            raise ValueError('checks and evidence required')
        for check in finding.checks:
            text(check)
        for ref in finding.evidence_ids:
            text(ref, 100)
        if len(set(finding.evidence_ids)) != len(finding.evidence_ids) or any(ref not in ids for ref in finding.evidence_ids):
            raise ValueError('unknown or duplicate evidence reference')
    for limitation in report.limitations:
        text(limitation)


def report_from_dict(data) -> AnalysisReport:
    """Strict wire boundary. Only this parser converts JSON lists to internal tuples."""
    def fields(value, expected):
        if type(value) is not dict or set(value) != set(expected):
            raise ValueError('unexpected fields')
    def array(value, maximum):
        if type(value) is not list or len(value) > maximum:
            raise ValueError('JSON array required')
        return tuple(value)
    fields(data, ('version', 'context_id', 'provider', 'model', 'generated_at', 'findings', 'limitations'))
    findings = []
    for item in array(data['findings'], 5):
        fields(item, ('observation', 'hypothesis', 'checks', 'recommendation', 'evidence_ids'))
        findings.append(Finding(item['observation'], item['hypothesis'], array(item['checks'], 5), item['recommendation'], array(item['evidence_ids'], 10)))
    return AnalysisReport(data['version'], data['context_id'], data['provider'], data['model'], data['generated_at'], tuple(findings), array(data['limitations'], 20))


def validate_ticket(ticket, now):
    if not isinstance(ticket, AnalysisTicket) or type(ticket.started_at) is not int or ticket.started_at < 0 or ticket.started_at > now + 60:
        raise ValueError('invalid issued ticket')
    # Validate observation freshness at inference start, not after a bounded inference.
    validate_context(ticket.context, ticket.started_at)
    if now > ticket.deadline:
        raise TimeoutError('analysis deadline exceeded')


def _publish(ticket, report, publisher, source, principal, now):
    validate_ticket(ticket, now)
    validate_report(ticket.context, report, now)
    if source not in ('server-worker', 'mcp-client'):
        raise ValueError('invalid source')
    text(principal, 100)
    canonical = json.dumps({'context': asdict(ticket.context), 'report': asdict(report), 'source': source, 'principal': principal}, sort_keys=True, separators=(',', ':'), allow_nan=False)
    publication = Publication(ticket.context, report, source, principal, int(now), now-ticket.context.window_end > 3600, hashlib.sha256(canonical.encode()).hexdigest())
    return publisher.publish(publication)


def submit_analysis(context_id: str, report: AnalysisReport, contexts: ContextStore,
                    publisher: NotePublisher, *, principal: str, clock: Callable[[], float] = time.time) -> str:
    """Authenticated handler supplies principal; AI can submit no replacement context."""
    text(context_id, 100)
    ticket = contexts.get(context_id)
    if ticket.context.context_id != context_id:
        raise ValueError('context store mismatch')
    return _publish(ticket, report, publisher, 'mcp-client', principal, clock())


def analyze_and_publish(context: AnalysisContext, provider: AnalysisProvider, publisher: NotePublisher,
                        *, principal: str, clock: Callable[[], float] = time.time) -> str:
    started = int(clock())
    ticket = AnalysisTicket(context, started)
    validate_ticket(ticket, started)
    report = provider.analyze(context, ticket.deadline)
    return _publish(ticket, report, publisher, 'server-worker', principal, clock())
