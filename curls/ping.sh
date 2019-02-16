#!/bin/bash

curl -v -X GET \
    -H "Content-Type: application/json" \
    http://172.180.0.4:9000/api/ping

echo
