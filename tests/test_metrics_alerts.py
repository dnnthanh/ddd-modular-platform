"""Smoke coverage for local offline metric/alert provisioning (no Docker needed)."""
import json
import pathlib
import sys
import unittest

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'infra/observer'))
from scrape_metrics import parse_exposition, gauge


class MetricsAlertTests(unittest.TestCase):
    def test_metric_parser_preserves_labels_and_handles_counter(self):
        sample = '\n'.join([
            '# TYPE pg_up gauge',
            'pg_up 1',
            '# TYPE pg_stat_database_deadlocks counter',
            'pg_stat_database_deadlocks{datname="modulardb"} 12',
            '# TYPE secret_api_token gauge',
            'secret_api_token 999',
        ])
        metrics = parse_exposition(sample, {'service': 'postgres', 'job': 'postgres'}, 100)
        self.assertEqual({'pg_up', 'pg_stat_database_deadlocks'}, {m['name'] for m in metrics})
        pg = next(m for m in metrics if m['name'] == 'pg_up')
        self.assertEqual(1, pg['gauge']['dataPoints'][0]['asDouble'])
        db = next(m for m in metrics if m['name'] == 'pg_stat_database_deadlocks')
        self.assertTrue(db['sum']['isMonotonic'])
        self.assertIn({'key': 'datname', 'value': {'stringValue': 'modulardb'}},
                      db['sum']['dataPoints'][0]['attributes'])

    def test_scrape_success_gauge(self):
        data = gauge('ddd_metrics_scrape_success', 0, {'job': 'redis'}, 12)
        self.assertEqual(0, data['gauge']['dataPoints'][0]['asDouble'])

    def test_datasource_localhost_only_in_all_in_one_container(self):
        compose = yaml.safe_load((ROOT/'compose.yaml').read_text())
        self.assertEqual('http://lgtm:4318', compose['x-java-otel']['OTEL_EXPORTER_OTLP_ENDPOINT'])
        for name in ('postgres-exporter', 'redis-exporter', 'postgres', 'redis', 'docker-socket-proxy', 'alloy'):
            self.assertNotIn('ports', compose['services'][name])
        self.assertEqual(1, len(compose['services']['lgtm']['ports']))
        self.assertEqual('/otel-lgtm/grafana/conf/provisioning',
                         compose['services']['lgtm']['environment']['GF_PATHS_PROVISIONING'])
        self.assertNotIn('ddd-prometheus', (ROOT/'infra/lgtm/provisioning/alerting/rules.yaml').read_text())

    def test_alert_rules_have_unique_ids_and_telegram_is_opt_in(self):
        alert = yaml.safe_load((ROOT/'infra/lgtm/provisioning/alerting/rules.yaml').read_text())
        rules = alert['groups'][0]['rules']
        self.assertGreaterEqual(len(rules), 10)
        self.assertEqual(len(rules), len({r['uid'] for r in rules}))
        self.assertTrue(all(r['data'] and r['condition'] == 'C' for r in rules))
        self.assertIn('infra/lgtm/provisioning/alerting/telegram.yaml', (ROOT/'.gitignore').read_text())
        self.assertTrue((ROOT/'infra/lgtm/telegram.yaml.example').exists())

    def test_dashboards_are_valid_and_have_jvm_db_panels(self):
        for f in ('jvm-hikari.json', 'postgres-redis.json'):
            dashboard=json.loads((ROOT/'infra/lgtm/dashboards'/f).read_text())
            self.assertGreaterEqual(len(dashboard['panels']), 10)
            self.assertTrue(all(p['targets'][0]['expr'] for p in dashboard['panels']))


if __name__ == '__main__':
    unittest.main()
