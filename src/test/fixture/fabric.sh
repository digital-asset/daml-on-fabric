#!/bin/bash
# Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

################################################################################

# simple batch script making it easier to cleanup and start a relatively fresh fabric env.

if [[ ! -e "docker-compose.yaml" ]];then
  echo "docker-compose.yaml not found."
  exit 8
fi

if [[ "${DOCKER_COMPOSE_FILE}" == "" ]]; then
  DOCKER_COMPOSE_FILE="docker-compose.yaml"
fi

if [[ "${DOCKER_NETWORK}" == "" ]]; then
  DOCKER_NETWORK="damlonfabric"
fi

export ORG_HYPERLEDGER_FABRIC_SDKTEST_VERSION="2.2.0"
export IMAGE_TAG_FABRIC_CA=":2.2"
export IMAGE_TAG_FABRIC=":2.2"

function clean(){

  rm -rf /var/hyperledger/*

  if [[ -e "/tmp/HFCSampletest.properties" ]];then
    rm -f "/tmp/HFCSampletest.properties"
  fi

  lines=`docker ps -a | grep 'dev-peer' | wc -l`

  if [[ "$lines" -gt 0 ]]; then
    docker ps -a | grep 'dev-peer' | awk '{print $1}' | xargs docker rm -f
  fi

  lines=`docker images | grep 'dev-peer' | grep 'dev-peer' | wc -l`
  if [[ "$lines" -gt 0 ]]; then
    docker images | grep 'dev-peer' | awk '{print $1}' | xargs docker rmi -f
  fi

}

function updetached() {

  if [[ "$ORG_HYPERLEDGER_FABRIC_SDKTEST_VERSION" == "1.0.0" ]]; then
    docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} up -d --force-recreate ca0 ca1 peer1.org1.example.com peer1.org2.example.com
  else
    docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} up -d --force-recreate
fi

}

function upNetworkDetached() {

    docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} up -d --force-recreate orderer.example.com peer0.org1.example.com peer0.org2.example.com configtxlator

}

function upDamlOnFabricDetached() {

    docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} rm -sf  daml-on-fabric-1 daml-on-fabric-2

    docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} up -d --no-deps daml-on-fabric-1 daml-on-fabric-2

}

function up(){

  if [[ "$ORG_HYPERLEDGER_FABRIC_SDKTEST_VERSION" == "1.0.0" ]]; then
    docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} up --force-recreate ca0 ca1 peer1.org1.example.com peer1.org2.example.com
  else
    docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} up --force-recreate
fi

}

function down(){
  docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} down;
}

function stop (){
  docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} stop;
}

function start (){
  docker-compose -p ${DOCKER_NETWORK} -f ${DOCKER_COMPOSE_FILE} start;
}


for opt in "$@"
do

    case "$opt" in
        up)
            up
            ;;
        updetached)
            updetached
            ;;
        upNetworkDetached)
            upNetworkDetached
            ;;
        upDamlOnFabricDetached)
            upDamlOnFabricDetached
            ;;
        down)
            down
            ;;
        stop)
            stop
            ;;
        start)
            start
            ;;
        clean)
            clean
            ;;
        restart)
            down
            clean
            up
            ;;

        *)
            echo $"Usage: $0 {up|down|start|stop|clean|restart|updetached|upNetworkDetached|upDamlOnFabricDetached}"
            exit 1

esac
done


