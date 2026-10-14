#!/bin/sh
set -eu
# In a multi-network container, publish the IPv4 source address used to reach Eureka.
# That is the shared application network, not the private database network.
if [ "${OTEL_SERVICE_NAME:-}" = modular-app ]; then
  EUREKA_IP="$(getent ahostsv4 eureka | awk 'NR==1{print $1}')"
  if [ -n "$EUREKA_IP" ]; then
    APP_IP="$(ip -4 route get "$EUREKA_IP" | sed -n 's/.* src \([0-9.]*\).*/\1/p' | head -1)"
    if [ -n "$APP_IP" ]; then
      export EUREKA_INSTANCE_IP_ADDRESS="$APP_IP"
      echo "Eureka advertised address selected from application network: $APP_IP"
    fi
  fi
fi
exec java -jar /app/app.jar
