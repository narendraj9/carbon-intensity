#!/usr/bin/env bash

export INFLUXDB_TOKEN="${INFLUXDB_TOKEN:=set-up-token-through-env}"
export INFLUXDB_USERNAME="${INFLUXDB_USER:=admin}"
export INFLUXDB_PASSWORD="${INFLUXDB_PASSWORD:=password}"
export INFLUXDB_ORG="CarbonIntensity"
export INFLUXDB_BUCKET="CO2/kWh"

echo "Starting InfluxDB."
docker-compose up -d influxdb

# If `curl` is available on the system, use it to check influxDB
# readiness. Otherwise just wait for some time.
if [[ `which curl` ]]; then
    echo "Waiting for InfluxDB to be ready."
    while [[ `curl -s -o /dev/null http://localhost:9999/ping -w "%{http_code}"` != "200" ]] ; do
        echo -n "."
        sleep 2s;
    done
else
    echo "Waiting for 15 seconds for influxDB to come up."
    sleep 15s;
fi

echo "Configuring InfluxDB with a user, org and token."
docker-compose exec influxdb influx setup        \
               --username "${INFLUXDB_USERNAME}" \
               --password "${INFLUXDB_PASSWORD}" \
               --org "${INFLUXDB_ORG}"           \
               --bucket "${INFLUXDB_BUCKET}"     \
               --token  "${INFLUXDB_TOKEN}"      \
               --force

echo "Starting the application."
docker-compose up carbon-intensity
