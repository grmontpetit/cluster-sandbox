#!/usr/bin/env bash

## docker-compose ##
echo "Generating docker-compose gif..."
terminalizer render cluster-up.yml --output cluster-up-temp.gif > /dev/null
convert cluster-up-temp.gif -fuzz 20% -layers Optimize cluster-up.gif
rm cluster-up-temp.gif
echo "docker-compose gif complete."

## cluster-members ##
echo "Generating cluster-members gif..."
terminalizer render cluster-members.yml --output cluster-members-temp.gif > /dev/null
convert cluster-members-temp.gif -fuzz 20%  -layers Optimize cluster-members.gif
rm cluster-members-temp.gif
echo "cluster-members gif complete."

## dig ##
echo "Generating dig gif..."
terminalizer render dig.yml --output dig-temp.gif > /dev/null
convert dig-temp.gif -fuzz 20% -layers Optimize dig.gif
rm dig-temp.gif
echo "dig gif complete."
