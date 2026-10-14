"""Small, dependency-free Prometheus text → OTLP HTTP metric bridge.

The LGTM image contains an OTLP receiver but does not automatically discover
Prometheus exporters. This bridge scrapes a *curated* set of exported metrics,
keeping backend/DB credentials off the observability UI. It is not intended to
replace a full Prometheus receiver in high-scale environments.
"""
import json
import math
import re
import time
import urllib.error
import urllib.request

START_TIME_NS = time.time_ns()

SAMPLE = re.compile(r'^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})?\s+([^\s]+)(?:\s+.*)?$')
LABEL = re.compile(r'([a-zA-Z_][a-zA-Z0-9_]*)="((?:\\.|[^"\\])*)"(?:,|$)')
TYPE = re.compile(r'^#\s+TYPE\s+([a-zA-Z_:][a-zA-Z0-9_:]*)\s+(\w+)')

WANTED_PREFIX = ('jvm_', 'hikaricp_', 'http_server_requests_', 'process_cpu_', 'system_cpu_')
WANTED_EXACT = {
    'pg_up', 'pg_stat_database_numbackends', 'pg_stat_database_deadlocks',
    'pg_stat_database_xact_commit', 'pg_stat_database_xact_rollback',
    'pg_stat_database_blks_hit', 'pg_stat_database_blks_read',
    'pg_stat_database_size_bytes', 'pg_settings_max_connections',
    'redis_up', 'redis_connected_clients', 'redis_memory_used_bytes',
    'redis_memory_max_bytes', 'redis_memory_used_rss_bytes',
    'redis_evicted_keys_total', 'redis_rejected_connections_total',
    'redis_keyspace_hits_total', 'redis_keyspace_misses_total',
}


def parse_exposition(payload, labels, stamp):
    """Return OTLP Metric objects with curated points; never process exemplars."""
    types = {}
    metrics = {}
    for line in payload.splitlines():
        typ = TYPE.match(line)
        if typ:
            types[typ.group(1)] = typ.group(2).lower()
            continue
        if not line or line.startswith('#'):
            continue
        parsed = SAMPLE.match(line)
        if not parsed:
            continue
        name, raw_labels, value = parsed.groups()
        if name not in WANTED_EXACT and not name.startswith(WANTED_PREFIX):
            continue
        try:
            number = float(value)
        except ValueError:
            continue
        if not math.isfinite(number):
            continue
        tags = dict(labels)
        if raw_labels:
            for match in LABEL.finditer(raw_labels + ','):
                # Don't overwrite project metadata with exporter-provided labels.
                key = match.group(1)
                if key not in ('job', 'service', 'instance', 'container'):
                    tags[key] = bytes(match.group(2), 'utf-8').decode('unicode_escape')
        attributes = [{'key': key, 'value': {'stringValue': str(val)}} for key, val in sorted(tags.items())]
        data = {'timeUnixNano': str(stamp), 'asDouble': number, 'attributes': attributes}
        parent = name.removesuffix('_total').removesuffix('_count').removesuffix('_sum')
        is_counter = types.get(name) == 'counter' or types.get(parent) in ('histogram', 'summary')
        kind = 'sum' if is_counter else 'gauge'
        if is_counter:
            data['startTimeUnixNano'] = str(START_TIME_NS)
        if name not in metrics:
            metrics[name] = {'name': name, kind: {'dataPoints': []}}
            if is_counter:
                metrics[name]['sum'].update({'isMonotonic': True, 'aggregationTemporality': 2})
        # Typical exporters do not change metric TYPE for the same metric name.
        metrics[name][kind]['dataPoints'].append(data)
    return list(metrics.values())


def gauge(name, value, labels, stamp):
    return {
        'name': name,
        'gauge': {'dataPoints': [{
            'timeUnixNano': str(stamp),
            'asDouble': float(value),
            'attributes': [{'key': k, 'value': {'stringValue': str(v)}} for k, v in sorted(labels.items())],
        }]},
    }


def read_endpoint(url):
    req = urllib.request.Request(url, headers={'Accept': 'text/plain'})
    with urllib.request.urlopen(req, timeout=4) as response:
        if response.status != 200:
            raise OSError('Exporter response not OK')
        return response.read(4_000_000).decode('utf-8', errors='replace')


def export(url, endpoint, targets, on_error=None):
    """targets: iterable of {job,service,instance,url[,container]}."""
    stamp = time.time_ns()
    all_metrics = []
    for target in targets:
        labels = {k: target[k] for k in ('job', 'service', 'instance', 'container') if k in target}
        try:
            data = read_endpoint(target['url'])
            all_metrics.extend(parse_exposition(data, labels, stamp))
            all_metrics.append(gauge('ddd_metrics_scrape_success', 1, labels, stamp))
        except (OSError, urllib.error.URLError, TimeoutError) as error:
            all_metrics.append(gauge('ddd_metrics_scrape_success', 0, labels, stamp))
            if on_error:
                on_error(target['job'], type(error).__name__)
    if not all_metrics:
        return
    document = {'resourceMetrics': [{
        'resource': {'attributes': [{'key': 'service.name', 'value': {'stringValue': 'ddd-metrics-bridge'}}]},
        'scopeMetrics': [{'scope': {'name': 'ddd.prometheus-exporters'}, 'metrics': all_metrics}],
    }]}
    request = urllib.request.Request(
        endpoint, data=json.dumps(document, separators=(',', ':')).encode('utf-8'),
        method='POST', headers={'Content-Type': 'application/json'},
    )
    try:
        with urllib.request.urlopen(request, timeout=8) as response:
            response.read()
    except (OSError, urllib.error.URLError, TimeoutError) as error:
        if on_error:
            on_error('lgtm-otlp', type(error).__name__)
