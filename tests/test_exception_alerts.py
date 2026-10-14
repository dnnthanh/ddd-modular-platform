"""Keep business 5xx distinct from telemetry failures and sanitized log alerts."""
from pathlib import Path
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[1]


class ExceptionAlertTests(unittest.TestCase):
    def setUp(self):
        data = yaml.safe_load((ROOT / 'infra/lgtm/provisioning/alerting/rules.yaml').read_text())
        self.rules = {rule['uid']: rule for group in data['groups'] for rule in group['rules']}

    def test_no_data_is_not_a_business_5xx_alert(self):
        rule = self.rules['ddd-http-5xx']
        self.assertEqual('KeepLast', rule['noDataState'])
        self.assertEqual('2m', rule['for'])
        self.assertIn('uri!~"/actuator.*"', rule['data'][0]['model']['expr'])
        self.assertEqual([0.1], rule['data'][2]['model']['conditions'][0]['evaluator']['params'])

    def test_log_rule_exports_exception_identity_without_raw_message(self):
        rule = self.rules['ddd-java-exception']
        self.assertEqual('OK', rule['noDataState'])
        self.assertEqual('loki', rule['data'][0]['datasourceUid'])
        query = rule['data'][0]['model']['expr']
        self.assertIn('level="ERROR"', query)
        self.assertIn('status=4[0-9]{2}', query)
        self.assertIn('sum by(service,exception_class,location,trace)', query)
        self.assertNotIn('sum by(message', query)
        self.assertNotIn('sum by(stack_trace', query)
        self.assertEqual('12', rule['annotations']['__panelId__'])

    def test_template_distinguishes_monitoring_and_missing_series(self):
        template = (ROOT / 'infra/lgtm/resources/notification-templates.yaml').read_text()
        for field in ['.Labels.datasource_uid', '.Labels.exception_class', '.Labels.location',
                      '.Labels.trace', '.Annotations.grafana_state_reason', '.PanelURL', '.Values']:
            self.assertIn(field, template)
        self.assertNotIn('HỆ THỐNG ĐÃ PHỤC HỒI', template)


if __name__ == '__main__':
    unittest.main()
