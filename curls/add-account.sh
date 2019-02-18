#!/usr/bin/env bash

curl -v -X POST \
    -H "Content-Type: application/json" \
    -d '{"username": "username", "password": "dff3f5gg2c", "nickname": "nickname"}' \
    http://172.180.0.4:9000/api/accounts

echo
