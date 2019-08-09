#!/usr/bin/env bash

# FROM https://medium.com/@salohyprivat/prometheus-and-grafana-d59f3b1ded8b

#INSTALL GRAFANA
# we start the container with a storage volume that not exits yet,
# do not worry, docker creates it for us

# we keep the config file for persistence and later use

# http://localhost:3000 for grafana

CONFIG_DIR=$(dirname $(readlink -f $0))

docker run -p 3000:3000 \
           -v ${CONFIG_DIR}/grafana.ini:/etc/grafana/grafana.ini \
           -v grafana-storage:/var/lib/grafana \
           grafana/grafana
