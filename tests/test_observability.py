"""Offline tests: instrumentation emits useful fields but never Docker env secrets."""
import importlib.util
import json
import os
import pathlib
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'infra/observer'))
spec = importlib.util.spec_from_file_location('observer', ROOT/'infra/observer/observer.py')
obs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(obs)


class ObserverTests(unittest.TestCase):
    def setUp(self):
        obs.last_metrics = ''
        obs.last_states = {}

    def test_docker_metadata_whitelist(self):
        cid = 'a'*64
        secret = 'super-sensitive-do-not-export'
        inspect = {
            'Id': cid, 'Name': '/ddd-platform-modular-app-1', 'Image': 'sha256:'+'b'*64,
            'Created': '2026-10-13T11:00:00Z', 'RestartCount': 2,
            'Config': {'Image': 'ddd/modular-app:v2', 'Env': ['DB_PASSWORD='+secret],
                       'Labels': {'com.docker.compose.service':'modular-app',
                                  'org.opencontainers.image.version':'v2',
                                  'org.opencontainers.image.revision':'f00baa'}},
            'State': {'Status':'running','Running':True,'StartedAt':'2026-10-13T11:00:00Z',
                      'Health': {'Status':'healthy'}}
        }
        with tempfile.TemporaryDirectory() as t:
            state = pathlib.Path(t)/'state.env';state.write_text('APP_REPLICAS=3\n')
            def mock_docker(route):
                if route.startswith('/containers/json'): return [{'Id':cid}]
                if route.startswith('/containers/'): return inspect
                if route.startswith('/images/'): return {'Id':inspect['Image']}
                raise AssertionError('unknown route '+route)
            with patch.object(obs,'docker_get',side_effect=mock_docker),patch.object(obs,'STATE_FILE',state),patch.object(obs,'event'),patch.object(obs,'export_otlp_metrics'),patch.object(obs.scrape_metrics,'export'):
                obs.refresh()
        metrics=obs.last_metrics
        self.assertIn('ddd_service_replicas_running{service="modular-app"} 1',metrics)
        self.assertIn('ddd_service_replicas_desired{service="modular-app"} 3',metrics)
        self.assertIn('ddd_container_restart_count',metrics)
        self.assertIn('image_ref="ddd/modular-app:v2"',metrics)
        self.assertIn('ddd_container_image_drift',metrics)
        self.assertNotIn(secret,metrics)

    def test_otel_metrics_export_uses_lgtm_json_protocol(self):
        from unittest.mock import MagicMock
        emitted = {}
        def fake_urlopen(request, timeout):
            emitted['url'] = request.full_url
            emitted['body'] = json.loads(request.data)
            response = MagicMock()
            response.__enter__.return_value.read.return_value = b''
            return response
        with patch.object(obs.urllib.request, 'urlopen', side_effect=fake_urlopen):
            obs.export_otlp_metrics([
                'ddd_observer_scrape_success 1\n',
                'ddd_container_restart_count{container="app-1",service="modular-app"} 3\n'
            ])
        self.assertEqual('http://lgtm:4318/v1/metrics', emitted['url'])
        data = emitted['body']['resourceMetrics'][0]['scopeMetrics'][0]['metrics']
        self.assertEqual('ddd_container_restart_count', data[1]['name'])
        self.assertEqual(3, data[1]['gauge']['dataPoints'][0]['asDouble'])

    def test_escape_labels(self):
        self.assertEqual(obs.escaped('abc"\\\n'), 'abc\\"\\\\\\n')

    def test_observer_emits_service_metrics_for_database_and_redis(self):
        cid = 'a' * 64
        inspect = {
            'Id': cid, 'Name': '/ddd-platform-postgres-1', 'Image': 'sha256:' + 'b' * 64,
            'Created': '2026-10-13T11:00:00Z', 'RestartCount': 0,
            'Config': {'Image': 'postgres:18', 'Labels': {'com.docker.compose.service': 'postgres'}},
            'State': {'Status': 'running', 'Running': True, 'StartedAt': '2026-10-13T11:00:00Z',
                      'Health': {'Status': 'healthy'}},
        }
        def mock_docker(route):
            if route.startswith('/containers/json'):
                return [{'Id': cid}]
            if route.startswith('/containers/'):
                return inspect
            if route.startswith('/images/'):
                return {'Id': inspect['Image']}
            raise AssertionError('unknown route ' + route)
        with patch.object(obs, 'docker_get', side_effect=mock_docker), \
             patch.object(obs, 'event'), patch.object(obs, 'export_otlp_metrics'), \
             patch.object(obs.scrape_metrics, 'export'):
            obs.refresh()
        self.assertIn('ddd_service_replicas_running{service="postgres"} 1', obs.last_metrics)
        self.assertIn('ddd_service_replicas_running{service="redis"} 0', obs.last_metrics)

    def test_deployment_event_whitelist(self):
        import subprocess
        with tempfile.TemporaryDirectory() as t:
            t=pathlib.Path(t)
            def snap(cid, name):
                return [{'Id':cid,'Name':'/'+name,'Image':'sha256:'+'f'*64,
                         'Config':{'Image':'ddd/modular-app:v1','Labels':{'com.docker.compose.service':'modular-app'},
                                   'Env':['JWT_PRIVATE_KEY=do-not-export']}}]
            (t/'before').write_text(json.dumps(snap('a'*64,'app-old')))
            (t/'after').write_text(json.dumps(snap('b'*64,'app-new')))
            subprocess.run(['python3',str(ROOT/'scripts/deployment_event.py'),'up','source-build replicas=3',str(t/'before'),str(t/'after'),str(t/'events.jsonl')],check=True)
            txt=(t/'events.jsonl').read_text()
            record=json.loads(txt)
            self.assertEqual(record['old_containers'][0]['container_id'],'a'*12)
            self.assertEqual(record['new_containers'][0]['container_id'],'b'*12)
            self.assertNotIn('do-not-export',txt)

if __name__ == '__main__':
    unittest.main()
