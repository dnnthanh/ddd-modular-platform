"""Static regression checks; integration still requires Maven/JDK25 + Docker."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
import json
import yaml

ROOT = Path(__file__).resolve().parents[1]

class LoggingTests(unittest.TestCase):
    def test_json_logback_configured_on_every_java_executable(self):
        for name in ('api-gateway', 'modular-monolith', 'discovery-server'):
            pom = (ROOT / name / 'pom.xml').read_text()
            self.assertIn('logstash-logback-encoder', pom)
            xml = ROOT / name / 'src/main/resources/logback-spring.xml'
            ET.parse(xml)
            self.assertIn('LogstashEncoder', xml.read_text())

    def test_http_audit_is_bounded_with_redaction(self):
        web = ROOT / 'platform-core/src/main/java/dev/demo/core/web'
        body = (web / 'HttpAuditFilter.java').read_text()
        self.assertIn('ContentCachingRequestWrapper', body)
        self.assertIn('BoundedResponseCapture', body)
        self.assertIn('addKeyValue("request_body"', body)
        self.assertIn('addKeyValue("response_body"', body)
        self.assertIn('getBodyPaths()', body)
        props = (web / 'HttpAuditProperties.java').read_text()
        self.assertIn('/api/private/catalog/**', props)
        self.assertIn('/api/public/auth/**', props)
        safe = (web / 'HttpAuditSanitizer.java').read_text()
        self.assertIn('"password"', safe)
        self.assertIn('"token"', safe)
        self.assertIn('"email"', safe)
        self.assertIn('redact(decoded', safe)
        self.assertIn('"[OMITTED:non-json]"', safe)

    def test_loki_log_dashboard_and_tempo_link(self):
        dashboard = json.loads((ROOT/'infra/lgtm/dashboards/observability.json').read_text())
        audit = next(p for p in dashboard['panels'] if p['id'] == 7)
        self.assertIn('event="http.audit"', audit['targets'][0]['expr'])
        reference = (ROOT/'infra/lgtm/provisioning/datasources.yaml').read_text()
        yaml_reference = '\n'.join(line[2:] for line in reference.splitlines() if line.startswith('# '))
        ds = yaml.safe_load('apiVersion:' + yaml_reference.split('apiVersion:', 1)[1])
        loki = next(d for d in ds['datasources'] if d['type'] == 'loki')
        self.assertIn('trace_id', loki['jsonData']['derivedFields'][0]['matcherRegex'])

    def test_optional_telegram_credentials_from_environment(self):
        cfg = yaml.safe_load((ROOT/'compose.yaml').read_text())
        self.assertIn('TELEGRAM_BOT_TOKEN', cfg['services']['lgtm']['environment'])
        self.assertIn('TELEGRAM_CHAT_ID', cfg['services']['lgtm']['environment'])
        script = (ROOT/'scripts/deploy.sh').read_text()
        self.assertIn('configure_telegram', script)
        self.assertIn('shutil.copyfile(template, target)', script)
        self.assertIn('target.unlink(missing_ok=True)', script)
        template = (ROOT/'infra/lgtm/telegram.yaml.example').read_text()
        self.assertIn('$TELEGRAM_BOT_TOKEN', template)
        self.assertIn('$TELEGRAM_CHAT_ID', template)

if __name__ == '__main__':
    unittest.main()
