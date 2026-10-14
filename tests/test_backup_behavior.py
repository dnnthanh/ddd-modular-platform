"""A failed dump must not publish a backup or delete an older valid backup."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT = Path(__file__).resolve().parents[1]

class BackupBehaviorTests(unittest.TestCase):
    def test_validated_backup_emits_success_event_after_publication(self):
        script = ROOT / 'infra/backup/backup.sh'
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); output = root / 'out'; output.mkdir(); commands = root / 'bin'; commands.mkdir()
            tools = {
                'flock': '#!/bin/sh\nexit 0\n',
                'pg_dump': '#!/bin/sh\nfor arg do case "$arg" in --file=*) printf archive > "${arg#--file=}";; esac; done\n',
                'pg_restore': '#!/bin/sh\nexit 0\n',
            }
            for name, body in tools.items():
                path = commands / name; path.write_text(body); path.chmod(0o755)
            env = dict(os.environ, PATH=str(commands) + ':' + os.environ['PATH'], BACKUP_DIR=str(output))
            result = subprocess.run(['sh', str(script), 'once'], env=env, capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr.decode())
            event = next((output / 'outbox').glob('*.json'))
            self.assertIn('"status":"success"', event.read_text())
            self.assertTrue((output / '.last-success').exists())

    def test_failed_dump_preserves_existing_backup_and_leaves_no_partial(self):
        script = ROOT / 'infra/backup/backup.sh'
        self.assertTrue(script.exists(), 'hourly backup implementation missing')
        with tempfile.TemporaryDirectory() as d:
            root = Path(d); output = root/'out'; output.mkdir()
            old = output/'modulardb-20000101T000000Z.dump'; old.write_bytes(b'valid old backup')
            os.utime(old, (946684800,946684800))
            commands = root/'bin'; commands.mkdir()
            flock = commands/'flock';flock.write_text('#!/bin/sh\nexit 0\n');flock.chmod(0o755)
            dump = commands/'pg_dump'; dump.write_text('#!/bin/sh\nprintf broken\nexit 1\n');dump.chmod(0o755)
            env = dict(os.environ, PATH=str(commands)+':'+os.environ['PATH'], BACKUP_DIR=str(output), BACKUP_RETENTION_DAYS='7')
            result = subprocess.run(['sh',str(script),'once'],env=env,capture_output=True)
            self.assertNotEqual(0,result.returncode)
            self.assertEqual(b'valid old backup',old.read_bytes())
            self.assertEqual([old], [p for p in output.iterdir()
                                    if p.name not in ('.backup.lock', 'outbox')])
            events = list((output/'outbox').glob('*.json'))
            self.assertEqual(1, len(events))
            self.assertIn('"status":"failure"', events[0].read_text())

    def test_publish_failure_in_scheduler_does_not_prune_old_backup(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);output=root/'out';output.mkdir()
            old=output/'modulardb-20000101T000000Z.dump';old.write_bytes(b'old valid backup')
            os.utime(old,(946684800,946684800))
            commands=root/'bin';commands.mkdir()
            tools={
                'pg_dump': '#!/bin/sh\nfor arg do case "$arg" in --file=*) printf archive > "${arg#--file=}";; esac; done\n',
                'pg_restore': '#!/bin/sh\nexit 0\n',
                'flock': '#!/bin/sh\nexit 0\n',
                'mv': '#!/bin/sh\nexit 1\n',
                'sleep': '#!/bin/sh\nexit 1\n',
            }
            for name,body in tools.items():
                path=commands/name;path.write_text(body);path.chmod(0o755)
            env=dict(os.environ,PATH=str(commands)+':'+os.environ['PATH'],BACKUP_DIR=str(output))
            result=subprocess.run(['sh',str(ROOT/'infra/backup/backup.sh')],env=env,capture_output=True,timeout=10)
            self.assertNotEqual(0,result.returncode)
            self.assertTrue(old.exists(),'failed publication pruned the last valid backup')
            self.assertNotIn(b'Backup completed',result.stdout)

    def test_health_requires_completion_marker_and_both_archive_files(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);env=dict(os.environ,BACKUP_DIR=d)
            command=['sh',str(ROOT/'infra/backup/health.sh')]
            name='modulardb-20261014T000000Z-123.dump'
            (root/name).write_bytes(b'archive')
            self.assertNotEqual(0,subprocess.run(command,env=env,capture_output=True).returncode)
            (root/'.last-success').write_text(name+'\n')
            self.assertNotEqual(0,subprocess.run(command,env=env,capture_output=True).returncode)
            import hashlib
            (root/(name+'.sha256')).write_text(hashlib.sha256(b'archive').hexdigest() + '  ' + name + '\n')
            self.assertEqual(0,subprocess.run(command,env=env,capture_output=True).returncode)
            (root/(name+'.sha256')).write_text('0' * 64 + '  ' + name + '\n')
            self.assertNotEqual(0,subprocess.run(command,env=env,capture_output=True).returncode)
            os.utime(root/'.last-success',(946684800,946684800))
            self.assertNotEqual(0,subprocess.run(command,env=env,capture_output=True).returncode)

if __name__ == '__main__': unittest.main()
