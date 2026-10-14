#!/usr/bin/env bash
# Local macOS LaunchAgent controller; Linux/VPS uses install-watchdog.sh + systemd.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LABEL=dev.ddd-platform.watchdog
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
DOMAIN="gui/$(id -u)"
RUNTIME="$HOME/Library/Application Support/ddd-platform/watchdog"
case "${1:-status}" in
  start)
    [[ $(uname -s) == Darwin ]] || { echo 'Linux: sudo ./scripts/install-watchdog.sh'; exit 1; }
    mkdir -p "$RUNTIME" "$HOME/Library/LaunchAgents"
    chmod 700 "$RUNTIME"
    python3 - "$ROOT" "$PLIST" "$(command -v python3)" "$(command -v docker)" "$RUNTIME" <<'PY'
import os,plistlib,sys
from pathlib import Path
root,plist,python,docker,runtime_dir=sys.argv[1:]
runtime=Path(runtime_dir)/'health-watchdog.py'
runtime.write_bytes((Path(root)/'scripts/health-watchdog.py').read_bytes());runtime.chmod(0o700)
config={'Label':'dev.ddd-platform.watchdog','ProgramArguments':[python,str(runtime),'--state-dir',runtime_dir+'/state'],
        'WorkingDirectory':runtime_dir,'RunAtLoad':True,'KeepAlive':True,
        'ThrottleInterval':15,'EnvironmentVariables':{'PATH':str(Path(docker).parent)+':'+os.environ.get('PATH','/usr/bin:/bin'),'DOCKER_CONFIG':str(Path.home()/'.docker')},
        'StandardOutPath':runtime_dir+'/daemon.log','StandardErrorPath':runtime_dir+'/daemon.log'}
with open(plist,'wb') as out:plistlib.dump(config,out)
os.chmod(plist,0o600)
config_path=Path(root)/'.env'
lines=[line for line in config_path.read_text().splitlines() if not line.startswith('WATCHDOG_STATE_DIR=')]
config_path.write_text('\n'.join(lines)+ '\nWATCHDOG_STATE_DIR='+runtime_dir+'/state\n')
config_path.chmod(0o600)
PY
    launchctl bootout "$DOMAIN/$LABEL" >/dev/null 2>&1 || true
    launchctl bootstrap "$DOMAIN" "$PLIST"
    echo 'Watchdog enabled as user LaunchAgent.';;
  stop) launchctl bootout "$DOMAIN/$LABEL"; echo 'Watchdog stopped.';;
  status) launchctl print "$DOMAIN/$LABEL" | sed -n '1,25p';;
  history) tail -n "${2:-30}" "$RUNTIME/state/events.jsonl";;
  *) echo 'Usage: ./scripts/watchdog.sh start|stop|status|history [lines]'; exit 1;;
esac
