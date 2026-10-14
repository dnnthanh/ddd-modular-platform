#!/usr/bin/env python3
"""Persist safe operation lifecycle metadata for the Grafana observer."""
import datetime as dt
import json
import pathlib
import sys


def load(path):
    items = json.loads(pathlib.Path(path).read_text(encoding='utf-8'))
    return {
        x['Id'][:12]: {
            'container_id': x['Id'][:12],
            'container': x.get('Name', '').lstrip('/'),
            'service': (x.get('Config', {}).get('Labels') or {}).get('com.docker.compose.service', ''),
            'image_ref': x.get('Config', {}).get('Image', ''),
            'image_sha': x.get('Image', '')[:19],
        }
        for x in items
    }


if __name__ == '__main__':
    action, detail, before_file, after_file, output_file = sys.argv[1:]
    before, after = load(before_file), load(after_file)
    event = {
        'timestamp': dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds'),
        'action': action,
        'detail': detail,
        'old_containers': [before[x] for x in sorted(before.keys() - after.keys())],
        'new_containers': [after[x] for x in sorted(after.keys() - before.keys())],
        'retained_container_count': len(before.keys() & after.keys()),
    }
    with open(output_file, 'a', encoding='utf-8') as output:
        output.write(json.dumps(event, ensure_ascii=True, separators=(',', ':')) + '\n')
