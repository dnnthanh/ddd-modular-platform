"""Bounded, at-least-once delivery of backup status events to Telegram.

An event is deleted only after Telegram accepts it. A process crash between
acceptance and deletion can repeat one message; Telegram does not offer an
idempotency key, so exactly-once delivery is not possible here.
"""
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request


class BackupNotifier:
    def __init__(self, outbox, state_dir, token, chat_id, template_path,
                 api_base='https://api.telegram.org'):
        self.outbox = Path(outbox)
        self.state_dir = Path(state_dir)
        self.token = token
        self.chat_id = chat_id
        self.template_path = Path(template_path)
        self.api_base = api_base.rstrip('/')

    def _load_state(self):
        path = self.state_dir / 'cursor.json'
        try:
            data = json.loads(path.read_text(encoding='utf-8'))
            if not isinstance(data, dict):
                raise ValueError('invalid notifier state')
            retry_after = float(data.get('retry_after', 0))
            attempts = int(data.get('attempts', 0))
            if retry_after >= 0 and attempts >= 0:
                data['retry_after'] = retry_after
                data['attempts'] = attempts
                if 'missing_since' in data and float(data['missing_since']) < 0:
                    data.pop('missing_since')
                return data
        except (OSError, ValueError, TypeError):
            pass
        return {'retry_after': 0, 'attempts': 0}

    def _save_state(self, data):
        self.state_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
        temporary = self.state_dir / '.cursor.json.partial'
        temporary.write_text(json.dumps(data, separators=(',', ':')), encoding='utf-8')
        os.chmod(temporary, 0o600)
        temporary.replace(self.state_dir / 'cursor.json')

    def _templates(self):
        data = json.loads(self.template_path.read_text(encoding='utf-8'))
        if not isinstance(data.get('success'), str) or not isinstance(data.get('failure'), str):
            raise ValueError('backup notification template is invalid')
        return data

    @staticmethod
    def _event(path):
        data = json.loads(path.read_text(encoding='utf-8'))
        if data.get('status') not in ('success', 'failure'):
            raise ValueError('invalid backup event status')
        identifier = str(data.get('id', ''))
        filename = str(data.get('filename', ''))
        if not identifier or not filename or '/' in filename or '\\' in filename:
            raise ValueError('invalid backup event')
        return identifier, data['status'], filename

    def _send(self, text):
        token = urllib.parse.quote(self.token, safe='')
        url = f'{self.api_base}/bot{token}/sendMessage'
        payload = json.dumps({'chat_id': self.chat_id, 'text': text}, separators=(',', ':')).encode('utf-8')
        request = urllib.request.Request(url, data=payload, method='POST',
                                         headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(request, timeout=8) as response:
            if response.status != 200:
                raise OSError('telegram returned non-OK status')
            response_body = json.loads(response.read(1_000_000))
            if response_body.get('ok') is not True:
                raise OSError('telegram rejected message')

    def poll(self, now=None):
        """Send queued events in order. Errors are deliberately not logged with URLs."""
        if not self.token or not self.chat_id:
            return
        now = time.time() if now is None else now
        state = self._load_state()
        if now < float(state.get('retry_after', 0)):
            return
        try:
            templates = self._templates()
            events = sorted(self.outbox.glob('*.json')) if self.outbox.is_dir() else []
            delivered = False
            for path in events:
                _identifier, status, filename = self._event(path)
                self._send(templates[status].format(filename=filename))
                # Keep undelivered files: losing an acknowledgement is worse than
                # a duplicate Telegram notification after a process crash.
                path.unlink()
                delivered = True
            if delivered:
                state.update({'retry_after': 0, 'attempts': 0})
                self._save_state(state)
        except (OSError, ValueError, KeyError, urllib.error.URLError, TimeoutError):
            attempts = min(int(state.get('attempts', 0)) + 1, 8)
            state['attempts'] = attempts
            state['retry_after'] = now + min(3600, 30 * (2 ** (attempts - 1)))
            self._save_state(state)

    def notify_missing(self, now=None, grace_seconds=7500):
        """Avoid a startup false positive while the first scheduled dump is pending."""
        if not self.token or not self.chat_id:
            return
        now = time.time() if now is None else now
        state = self._load_state()
        since = float(state.get('missing_since', 0)) or now
        state['missing_since'] = since
        if now - since < grace_seconds or state.get('missing_delivered') or now < float(state.get('retry_after', 0)):
            self._save_state(state)
            return
        try:
            self._send(self._templates()['failure'].format(filename='modulardb-unknown.dump'))
            state.update({'missing_delivered': True, 'retry_after': 0, 'attempts': 0})
            self._save_state(state)
        except (OSError, ValueError, KeyError, urllib.error.URLError, TimeoutError):
            attempts = min(int(state.get('attempts', 0)) + 1, 8)
            state['attempts'] = attempts
            state['retry_after'] = now + min(3600, 30 * (2 ** (attempts - 1)))
            self._save_state(state)

    def notify_stale(self, filename, now=None):
        """Notify once per completion marker when the producer has gone stale."""
        if not self.token or not self.chat_id:
            return
        now = time.time() if now is None else now
        state = self._load_state()
        key = 'stale:' + filename
        if state.get('stale_delivered') == key or now < float(state.get('retry_after', 0)):
            return
        try:
            self._send(self._templates()['failure'].format(filename=filename))
            state.update({'stale_delivered': key, 'retry_after': 0, 'attempts': 0})
            self._save_state(state)
        except (OSError, ValueError, KeyError, urllib.error.URLError, TimeoutError):
            attempts = min(int(state.get('attempts', 0)) + 1, 8)
            state['attempts'] = attempts
            state['retry_after'] = now + min(3600, 30 * (2 ** (attempts - 1)))
            self._save_state(state)
