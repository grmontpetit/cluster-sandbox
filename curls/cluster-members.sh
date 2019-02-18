#!/usr/bin/env bash

curl -X GET \
    -H "Content-Type: application/json" \
    http://172.180.0.4:8558/cluster/members

echo
