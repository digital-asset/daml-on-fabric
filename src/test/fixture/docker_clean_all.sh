#!/bin/bash
# Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0


echo "Stop every running Docker container"
docker stop $(docker ps -a -q)

echo "Delete dangling docker images"
docker rmi $(docker images -f 'dangling=true' -q)

echo "Delete Docker containers associated with fabric network"
docker rm -f $(docker ps -a -q -f network=damlonfabric_ci_default)
docker rm -f $(docker ps -a -q -f network=damlonfabric_default)

echo "Delete daml-on-fabric images"
docker rmi -f $(docker images -q "digitalasset/daml-on-fabric*")

echo "Delete chaincode images"
docker rmi -f $(docker images -q "dev-peer*")

echo "Delete base images used in build"
grep -hr --include=\Dockerfile 'FROM ' ../../../ | cut -d' ' -f 2 | xargs docker rmi

echo "Delete hyperledger fabric images"
docker rmi -f $(docker images -q "hyperledger/fabric-*")
