#!/bin/bash

curl -v -X POST \
    -H "Content-Type: application/json" \
    -d '{"username": "username", "password": "dff3f5gg2c"}' \
    http://172.180.0.4:9000/api/sessions

echo
