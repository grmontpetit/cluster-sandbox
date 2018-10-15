# Cluster Sandbox

This is a small proof of concept using Akka Clustering and Docker.

[![Build Status](https://travis-ci.org/sniggel/cluster-sandbox.svg?branch=master)](https://travis-ci.org/sniggel/cluster-sandbox)

## TLDR

Using Akka Clustering with ClusterBoostrap and AkkaManagement, it is possible to create an Akka cluster by using DNS records.
Once a node is started, it will query the DNS server for A and SRV records for a given cluster name and service namespace.

## Project Architecture

This project is running 5 dockers containers, 2 Akka nodes forming a cluster, 1 DNS server running Bind9 and 2 cassandra servers used for Akka Persistence.

![diag1](images/diag1.jpeg)

Part of the cluster, namely the Entity is using Akka-Typed which at the time this page was written, was a work in progress at Lightbend. Akka-Typed is the next version of Akka using Typed actors.

## Usage

Simply invoke `docker-compose -f docker-compose.yml up` to start the cluster and wait for the cluster to come up:

![Alt Text](images/docker-compose1.gif)

Once the cluster is running we can check that the members have succesfully joined togheter (httpie command output):

![Alt Text](images/httpget.gif)

We can also check the output of `dig` to check the dns zones and records:

![Alt Text](images/dig.gif)

## How it works

When a node is started, it asks the DNS for A and SRV records and then try to establish a connection to all the cluster nodes with a gossip protocol. 

The A records are used to tell Akka "where" the nodes are while the SRV records tells Akka "how" to connect to the nodes (namely the ports).

Once the nodes are discovered, an election process is initiated between the members of the cluster. After the election process, a lead is elected and the shards are distributed amongst the shard regions.

Note that for using ClusterBoostrap and AkkaManagement, the documentation specifically says not to use any seed-nodes.

Eventually, a better solution for a production environment is to use the kubernetes api to resolve new nodes joining a namespace. This is supported by akka.

## Notes

To push to docker hub, you must login with the docker CLI first.