from debian:stretch-slim

LABEL maintainer="Gabriel Robitaille-Montpetit"
RUN apt-get update
RUN apt-get install bind9 bind9utils bind9-doc -y
RUN mkdir -p /etc/bind/zones/akka.org

ADD run.sh /root/run.sh
ADD etc/bind/zones/akka.org/db.akka.org /etc/bind/zones/akka.org/db.akka.org
ADD etc/bind/zones/akka.org/db.17.172 /etc/bind/zones/akka.org/db.17.172
ADD etc/bind/named.conf.local /etc/bind/named.conf.local
ADD etc/bind/named.conf.options /etc/bind/named.conf.options

EXPOSE 53:53/udp

CMD /root/run.sh