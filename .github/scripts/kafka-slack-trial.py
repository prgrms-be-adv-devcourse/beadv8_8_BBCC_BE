import base64
import json
import os
import re
import time
import urllib.error
import urllib.request

root = "https://grafana.kidly.kro.kr"
path = "/api/v1/provisioning/alert-rules"
source_uid = "kidly-kafka-consumer-stalled"
uid = "kidly-kafka-slack-trial-984"
title = "[시험] Kafka 소비 진행 정지 #984"
auth = base64.b64encode(("admin:" + os.environ["GRAFANA_ADMIN_PASSWORD"]).encode()).decode()


def request(endpoint, method="GET", payload=None):
    headers = {"Authorization": "Basic " + auth, "Content-Type": "application/json"}
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(root + endpoint, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"{method} Grafana API returned HTTP {error.code}") from error


def metrics():
    try:
        body = request("/metrics").decode()
    except RuntimeError:
        return {}
    counts = {}
    for row in body.splitlines():
        if not row.startswith(("alertmanager_notifications_total{", "alertmanager_notifications_failed_total{")):
            continue
        if re.search(r'integration="slack"', row):
            name, value = row.rsplit(" ", 1)
            key = name.split("{", 1)[0]
            counts[key] = counts.get(key, 0) + float(value)
    return counts


def active():
    alerts = json.loads(request("/api/alertmanager/grafana/api/v2/alerts"))
    return any(alert.get("labels", {}).get("alertname") == title for alert in alerts)


if os.environ.get("TRIAL_CLEANUP") == "1":
    try:
        rule = json.loads(request(path + "/" + uid))
    except RuntimeError as error:
        if not str(error).endswith("HTTP 404"):
            raise
    else:
        if rule.get("title") != title:
            raise RuntimeError("Refusing to delete unexpected rule")
        request(path + "/" + uid, "DELETE")
        print("Temporary rule removed", flush=True)
    raise SystemExit

original = json.loads(request(path + "/" + source_uid))
if original.get("isPaused") is not True:
    raise RuntimeError("Expected the deployed Kafka rule to be paused")
baseline = metrics()
print("Slack counters available:", bool(baseline), flush=True)
data = original["data"]
data[0]["model"]["expr"] = f'label_replace(vector(time() < bool {int(time.time()) + 155}), "trial", "984", "__name__", ".*")'
data[0]["model"]["instant"] = True
annotations = original["annotations"]
for key, value in {"env": "dev", "consumergroup": "kidly.trial", "topic": "kidly.trial.events"}.items():
    annotations = {name: text.replace("{{ $labels." + key + " }}", value) for name, text in annotations.items()}
annotations["summary"] = "[시험] " + annotations["summary"]
annotations["recovery_summary"] = "[시험] " + annotations["recovery_summary"]
rule = {
    "uid": uid, "title": title, "orgID": 1, "folderUID": original["folderUID"],
    "ruleGroup": "kafka-slack-trial-984", "condition": original["condition"],
    "data": data, "noDataState": "OK", "execErrState": "OK", "for": "0s", "isPaused": False,
    "labels": {"env": "dev", "severity": "warning", "consumergroup": "kidly.trial", "topic": "kidly.trial.events"},
    "annotations": annotations,
}
request(path, "POST", rule)
print("Temporary rule created", flush=True)
seen = False
resolved_at = None
start = time.monotonic()
while time.monotonic() - start < 550:
    firing = active()
    if firing and not seen:
        seen = True
        print("Grafana observed firing", flush=True)
    if seen and not firing and resolved_at is None:
        resolved_at = time.monotonic()
        print("Grafana observed resolution", flush=True)
    if resolved_at is not None:
        # The shared notification policy sends grouped updates every five minutes.
        current = metrics()
        if baseline and current.get("alertmanager_notifications_total", 0) - baseline.get("alertmanager_notifications_total", 0) >= 2 and time.monotonic() - resolved_at > 120:
            break
        if time.monotonic() - resolved_at > 330:
            break
    time.sleep(10)
final = metrics()
for name, value in final.items():
    if name in baseline:
        print(name, "delta", value - baseline[name], flush=True)
if resolved_at is None:
    raise RuntimeError("Firing and resolution could not both be confirmed")
