#!/bin/bash

/etc/init.d/bind9 restart

# Let the container live.
while [ 1 ];
do
  sleep 10
done