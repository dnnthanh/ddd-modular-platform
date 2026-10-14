"""The backup notifier delivers only status and archive name to Telegram."""
import http.server
import importlib.util
import json
import pathlib
import tempfile
import threading
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('backup_notifier', ROOT / 'infra/backup/notifier.py')
notifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(notifier)


class _Telegram(http.server.BaseHTTPRequestHandler):
    received = []
    def do_POST(self):
        size = int(self.headers['Content-Length'])
        self.__class__.received.append((self.path, json.loads(self.rfile.read(size))))
        self.send_response(200); self.end_headers(); self.wfile.write(b'{"ok":true}')
    def log_message(self, *args): pass


class BackupNotifierTests(unittest.TestCase):
    def test_delivers_success_and_failure_from_outbox_without_error_details(self):
        server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), _Telegram)
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        try:
            with tempfile.TemporaryDirectory() as d:
                root = pathlib.Path(d); outbox = root / 'outbox'; outbox.mkdir()
                (outbox / '001.json').write_text(json.dumps({'id':'001','status':'success','filename':'modulardb-ok.dump'}))
                (outbox / '002.json').write_text(json.dumps({'id':'002','status':'failure','filename':'modulardb-bad.dump','error':'password=secret'}))
                template = root / 'template.json'
                template.write_text(json.dumps({'success':'SAO LUU THANH CONG: {filename}', 'failure':'SAO LUU THAT BAI: {filename}'}))
                delivery = notifier.BackupNotifier(outbox, root / 'state', 'token-value', '42', template,
                    f'http://127.0.0.1:{server.server_port}')
                delivery.poll()
                self.assertEqual([], list(outbox.glob('*.json')))
                bodies = [entry[1]['text'] for entry in _Telegram.received]
                self.assertEqual(['SAO LUU THANH CONG: modulardb-ok.dump', 'SAO LUU THAT BAI: modulardb-bad.dump'], bodies)
                self.assertNotIn('password=secret', repr(_Telegram.received))
        finally:
            server.shutdown(); server.server_close()

    def test_failed_delivery_uses_backoff_and_keeps_event(self):
        with tempfile.TemporaryDirectory() as d:
            root = pathlib.Path(d); outbox = root / 'outbox'; outbox.mkdir()
            (outbox / '001.json').write_text(json.dumps({'id':'001','status':'failure','filename':'modulardb-bad.dump'}))
            template = root / 'template.json'; template.write_text(json.dumps({'success':'ok {filename}', 'failure':'bad {filename}'}))
            delivery = notifier.BackupNotifier(outbox, root / 'state', 'token-value', '42', template, 'http://127.0.0.1:1')
            delivery.poll()
            state = json.loads((root/'state'/'cursor.json').read_text())
            self.assertGreater(state['retry_after'], 0)
            self.assertTrue((outbox / '001.json').exists())

    def test_invalid_saved_retry_state_is_replaced_instead_of_crashing_loop(self):
        with tempfile.TemporaryDirectory() as d:
            root = pathlib.Path(d); outbox = root / 'outbox'; outbox.mkdir()
            (outbox / '001.json').write_text(json.dumps(
                {'id':'001', 'status':'failure', 'filename':'modulardb-bad.dump'}))
            state = root / 'state'; state.mkdir()
            (state / 'cursor.json').write_text('{"retry_after":"not-a-number","attempts":-1}')
            template = root / 'template.json'
            template.write_text(json.dumps({'success':'ok {filename}', 'failure':'bad {filename}'}))
            delivery = notifier.BackupNotifier(outbox, state, 'token-value', '42', template, 'http://127.0.0.1:1')
            delivery.poll(now=1)
            self.assertEqual(1, json.loads((state / 'cursor.json').read_text())['attempts'])


if __name__ == '__main__': unittest.main()
