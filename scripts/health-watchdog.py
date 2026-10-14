#!/usr/bin/env python3
"""Optional HOST-SIDE health remediation with Docker admin privileges.

Not part of the deployment CLI and not enabled automatically. Restart only
persistently unhealthy app/gateway/eureka containers (never postgres/redis).
"""
import json
import subprocess
import sys
import time

POLL = max(5, int(sys.argv[1])) if len(sys.argv) > 1 else 10
UNHEALTHY_FOR = max(15, int(sys.argv[2])) if len(sys.argv) > 2 else 30
COOLDOWN = 90
ALLOWED = {'modular-app', 'gateway', 'eureka'}
first_unhealthy = {}
last_restart = {}


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True)


while True:
    now = time.monotonic()
    try:
        ids = docker('ps', '-aq', '--filter', 'label=com.docker.compose.project=ddd-platform').split()
        if ids:
            containers = json.loads(docker('inspect', *ids))
        else:
            containers = []
        active = set()
        for item in containers:
            service = (item.get('Config', {}).get('Labels') or {}).get('com.docker.compose.service')
            if service not in ALLOWED:
                continue
            cid = item['Id']
            active.add(cid)
            health = (item.get('State', {}).get('Health') or {}).get('Status')
            if health != 'unhealthy':
                first_unhealthy.pop(cid, None)
                continue
            first_unhealthy.setdefault(cid, now)
            if now - first_unhealthy[cid] >= UNHEALTHY_FOR and now - last_restart.get(cid, -1e8) >= COOLDOWN:
                print(f'restarting unhealthy {service} {cid[:12]}', flush=True)
                subprocess.run(['docker', 'restart', '-t', '35', cid], check=False)
                first_unhealthy.pop(cid, None)
                last_restart[cid] = now
        for cid in list(first_unhealthy):
            if cid not in active:
                first_unhealthy.pop(cid, None)
    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError) as exc:
        print(f'watchdog error: {type(exc).__name__}', flush=True)
    time.sleep(POLL)
