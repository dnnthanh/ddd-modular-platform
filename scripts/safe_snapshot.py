#!/usr/bin/env python3
"""Strip Docker inspect to explicit non-secret fields before persisting operation history."""
import json,sys
items=json.load(sys.stdin)
result=[]
for item in items:
    labels=(item.get('Config',{}).get('Labels') or {})
    safe_labels={name:labels.get(name) for name in (
        'com.docker.compose.project','com.docker.compose.service',
        'org.opencontainers.image.version','org.opencontainers.image.revision') if name in labels}
    result.append({
      'Id':item.get('Id'), 'Name':item.get('Name'), 'Image':item.get('Image'),
      'Config':{'Image':item.get('Config',{}).get('Image'),'Labels':safe_labels},
      'State':{'StartedAt':item.get('State',{}).get('StartedAt'),
               'Status':item.get('State',{}).get('Status')},
      'RestartCount':item.get('RestartCount',0),
    })
json.dump(result,sys.stdout,indent=2)
