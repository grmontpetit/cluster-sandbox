# Cluster Sandbox

This is a small proof of concept using Akka Clustering and Docker.

## TLDR

Using Akka Clustering with ClusterBoostrap and AkkaManagement, it is possible to create an Akka cluster by using DNS records.
Once a node is started, it will query the DNS server for A and SRV records for a given cluster name and service namespace.

## Project Architecture

This project is running 3 dockers containers, 2 Akka nodes forming a cluster and 1 DNS server running Bind9.

![diag1](images/diag1.jpeg)

The cluster is started by using docker-compose.

Part of the cluster, namely the Entity is using Akka-Typed which at the time this page was written, was a work in progress at Lightbend. Akka-Typed is the next version of Akka using Typed actors.

## Usage

Simply invoke `docker-compose -f docker-compose.yml up` to start the cluster.

## How it works

The process is best explained with this diagram:

// TODO complete me


## Notes

To push to docker hub, you must login with the docker CLI first.