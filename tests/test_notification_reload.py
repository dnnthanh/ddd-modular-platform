"""Exercise the real deploy command against a disposable project and Docker boundary."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[1]

class NotificationReloadTests(unittest.TestCase):
    def test_alerts_reload_uses_latest_resource_and_restarts_grafana(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d)
            for folder in ['scripts','infra/lgtm/resources','infra/lgtm/provisioning/alerting','.deploy','bin']:
                (root/folder).mkdir(parents=True)
            shutil.copyfile(ROOT/'scripts/deploy.sh',root/'scripts/deploy.sh')
            shutil.copyfile(ROOT/'infra/lgtm/telegram.yaml.example',root/'infra/lgtm/telegram.yaml.example')
            resource=root/'infra/lgtm/resources/notification-templates.yaml'
            resource.write_text('apiVersion: 1\ntemplates: []\n')
            (root/'.env').write_text('APP_REPLICAS=1\nTELEGRAM_BOT_TOKEN=test-only\nTELEGRAM_CHAT_ID=123\n')
            (root/'.deploy/state.env').write_text('APP_REPLICAS=1\n')
            docker=root/'bin/docker';docker.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$DOCKER_CALL_LOG"\n');docker.chmod(0o755)
            log=root/'calls'
            env=dict(os.environ,PATH=str(root/'bin')+':'+os.environ['PATH'],DOCKER_CALL_LOG=str(log))
            args=['bash',str(root/'scripts/deploy.sh'),'alerts']
            subprocess.run(args,env=env,cwd=root,check=True,capture_output=True)
            generated=root/'infra/lgtm/provisioning/alerting/notification-templates.yaml'
            self.assertEqual(resource.read_text(),generated.read_text())
            resource.write_text('apiVersion: 1\ntemplates: [{name: changed}]\n')
            subprocess.run(args,env=env,cwd=root,check=True,capture_output=True)
            self.assertIn('changed',generated.read_text())
            self.assertIn('--force-recreate lgtm',log.read_text())
            contact=(root/'infra/lgtm/provisioning/alerting/telegram.yaml').read_text()
            self.assertNotIn('test-only',contact)
            self.assertIn('$TELEGRAM_BOT_TOKEN',contact)

if __name__ == '__main__': unittest.main()
