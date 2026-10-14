#!/usr/bin/env python3
"""Host-side remediation and durable, redacted history for one Compose project."""
import argparse
import fcntl
import json
import os
import subprocess
import time
from pathlib import Path

ALLOWED = {'modular-app', 'gateway', 'eureka'}
RESULTS = {'recovered': 1, 'restart_failed': 2, 'recovery_timeout': 3,
           'pending': 4, 'stopped': 5, 'removed': 6}


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True, timeout=50)


class Watchdog:
    def __init__(self, directory, unhealthy_for=30, cooldown=90, recovery_timeout=180):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.unhealthy_for = unhealthy_for
        self.cooldown = cooldown
        self.recovery_timeout = recovery_timeout
        self.first_unhealthy = {}
        self.last_restart = {}
        self.pending = {}
        path = self.directory / 'state.json'
        # Invalid durable counters must not silently reset to zero.
        self.state = json.loads(path.read_text()) if path.exists() else {'services': {}}
        for cid, pending in self.state.get('pending', {}).items():
            self.pending[cid] = (pending['service'], time.monotonic() + max(0, pending['deadline'] - time.time()))

    def save(self):
        self.state['heartbeat'] = time.time()
        self.state['pending'] = {cid: {'service': service, 'deadline': time.time() + deadline - time.monotonic()}
                                 for cid, (service, deadline) in self.pending.items()}
        temporary = self.directory / 'state.json.tmp'
        temporary.write_text(json.dumps(self.state, sort_keys=True))
        os.chmod(temporary, 0o600)
        temporary.replace(self.directory / 'state.json')

    def record(self, service, cid, result):
        entry = self.state['services'].setdefault(service, {'restarts': 0, 'attempts': 0})
        entry.update(result=RESULTS[result], last_result_at=time.time())
        with (self.directory / 'events.jsonl').open('a') as output:
            output.write(json.dumps({'time': time.time(), 'service': service,
                                     'container_id': cid[:12], 'result': result}) + '\n')
        self.save()

    def tick(self, containers, now=None):
        now = time.monotonic() if now is None else now
        current = {item['Id']: item for item in containers}
        for cid, pending in list(self.pending.items()):
            service, deadline = pending
            item = current.get(cid)
            state = (item or {}).get('State') or {}
            health = (state.get('Health') or {}).get('Status')
            result = None
            if item is None:
                result = 'removed'
            elif not state.get('Running'):
                result = 'stopped'
            elif health == 'healthy':
                result = 'recovered'
            elif now >= deadline:
                result = 'recovery_timeout'
            if result:
                self.record(service, cid, result)
                del self.pending[cid]

        for cid, item in current.items():
            labels = (item.get('Config') or {}).get('Labels') or {}
            service = labels.get('com.docker.compose.service')
            state = item.get('State') or {}
            health = (state.get('Health') or {}).get('Status')
            # Never start a deliberately stopped container or interrupt startup grace.
            if (labels.get('com.docker.compose.project') != 'ddd-platform'
                    or service not in ALLOWED or not state.get('Running')
                    or state.get('Paused') or state.get('Restarting')
                    or health != 'unhealthy'):
                self.first_unhealthy.pop(cid, None)
                continue
            if cid in self.pending:
                continue
            first = self.first_unhealthy.setdefault(cid, now)
            entry = self.state['services'].get(service, {})
            # A daemon restart does not erase the service cooldown.
            wall_cooldown = time.time() - entry.get('last_attempt_at', 0)
            if (now - first < self.unhealthy_for or wall_cooldown < self.cooldown
                    or now - self.last_restart.get(cid, -1e8) < self.cooldown):
                continue
            # Inspect immediately before acting, including Running and project labels.
            fresh = json.loads(docker('inspect', cid))[0]
            fresh_state = fresh.get('State') or {}
            fresh_labels = (fresh.get('Config') or {}).get('Labels') or {}
            if (not fresh_state.get('Running') or fresh_state.get('Paused')
                    or fresh_state.get('Restarting')
                    or (fresh_state.get('Health') or {}).get('Status') != 'unhealthy'
                    or fresh_labels.get('com.docker.compose.project') != 'ddd-platform'
                    or fresh_labels.get('com.docker.compose.service') != service):
                self.first_unhealthy.pop(cid, None)
                continue
            entry = self.state['services'].setdefault(service, {'restarts': 0, 'attempts': 0})
            entry['attempts'] += 1
            entry['last_attempt_at'] = time.time()
            self.record(service, cid, 'pending')
            self.last_restart[cid] = now
            self.first_unhealthy.pop(cid, None)
            try:
                docker('restart', '-t', '35', cid)
            except (OSError, subprocess.SubprocessError):
                self.record(service, cid, 'restart_failed')
                continue
            entry['restarts'] += 1
            self.pending[cid] = (service, now + self.recovery_timeout)
            self.save()
        self.first_unhealthy = {cid: stamp for cid, stamp in self.first_unhealthy.items() if cid in current}
        self.save()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--state-dir', default=os.environ.get('WATCHDOG_STATE_DIR', '.deploy/watchdog'))
    parser.add_argument('--poll', type=int, default=10)
    parser.add_argument('--unhealthy-for', type=int, default=30)
    parser.add_argument('--cooldown', type=int, default=90)
    parser.add_argument('--recovery-timeout', type=int, default=180)
    args = parser.parse_args()
    os.umask(0o077)
    agent = Watchdog(args.state_dir, max(15, args.unhealthy_for), max(30, args.cooldown), max(60, args.recovery_timeout))
    with (agent.directory / 'daemon.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        while True:
            try:
                ids = docker('ps', '-aq', '--filter', 'label=com.docker.compose.project=ddd-platform').split()
                items = json.loads(docker('inspect', *ids)) if ids else []
                agent.tick(items)
            except (OSError, subprocess.SubprocessError, ValueError, KeyError) as error:
                print('watchdog error: ' + type(error).__name__, flush=True)
            time.sleep(max(5, args.poll))


if __name__ == '__main__':
    main()
