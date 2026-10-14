"""Deterministic watchdog behavior, without restarting real containers."""
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('watchdog', pathlib.Path(__file__).resolve().parents[1] / 'scripts/health-watchdog.py')
w = importlib.util.module_from_spec(spec)
spec.loader.exec_module(w)


def container(running=True, health='unhealthy', service='gateway'):
    return {'Id': 'a' * 64, 'Config': {'Labels': {'com.docker.compose.project': 'ddd-platform',
                                               'com.docker.compose.service': service}},
            'State': {'Running': running, 'Health': {'Status': health}}}


class WatchdogTests(unittest.TestCase):
    def test_continuous_unhealthy_then_recovery_and_persistence(self):
        with tempfile.TemporaryDirectory() as directory:
            agent = w.Watchdog(directory)
            item = container()
            with patch.object(w, 'docker', return_value=json.dumps([item])) as docker:
                agent.tick([item], 0); agent.tick([item], 29)
                docker.assert_not_called()
                agent.tick([item], 30)
                self.assertEqual(1, agent.state['services']['gateway']['restarts'])
                item['State']['Health']['Status'] = 'healthy'
                agent.tick([item], 40)
                self.assertEqual(1, agent.state['services']['gateway']['result'])
                self.assertEqual(1, w.Watchdog(directory).state['services']['gateway']['restarts'])

    def test_stopped_starting_database_and_interrupted_unhealthy_are_skipped(self):
        with tempfile.TemporaryDirectory() as directory:
            agent = w.Watchdog(directory)
            with patch.object(w, 'docker') as docker:
                for item in [container(False), container(health='starting'), container(service='postgres')]:
                    agent.tick([item], 0); agent.tick([item], 60)
                item = container(); agent.tick([item], 100)
                item['State']['Health']['Status'] = 'healthy'; agent.tick([item], 120)
                item['State']['Health']['Status'] = 'unhealthy'; agent.tick([item], 140)
                agent.tick([item], 169)
                docker.assert_not_called()

    def test_failed_restart_not_counted_and_cooldown_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            agent = w.Watchdog(directory); item = container()
            def command(*args):
                if args[0] == 'inspect': return json.dumps([item])
                raise subprocess.CalledProcessError(1, args)
            with patch.object(w, 'docker', side_effect=command) as docker:
                agent.tick([item], 0); agent.tick([item], 30)
                self.assertEqual(0, agent.state['services']['gateway']['restarts'])
                self.assertEqual(2, agent.state['services']['gateway']['result'])
                agent.tick([item], 60); agent.tick([item], 120)
                self.assertEqual(2, docker.call_count)

    def test_running_rechecked_before_action_and_recovery_timeout(self):
        with tempfile.TemporaryDirectory() as directory:
            agent = w.Watchdog(directory); item = container()
            with patch.object(w, 'docker', return_value=json.dumps([container(False)])) as docker:
                agent.tick([item], 0); agent.tick([item], 30)
                self.assertEqual(1, docker.call_count)
            with patch.object(w, 'docker', return_value=json.dumps([item])):
                agent.tick([item], 40); agent.tick([item], 70); agent.tick([item], 251)
                self.assertEqual(3, agent.state['services']['gateway']['result'])


if __name__ == '__main__':
    unittest.main()
