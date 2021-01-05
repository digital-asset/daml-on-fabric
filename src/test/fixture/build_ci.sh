#!/bin/bash
# Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

################################################################################


# This script builds Docker images required for CI (so that whole CI environment runs inside Docker-Compose)

set -e

cp ./ledger-api-test-tool.jar tmp
cp ../../../target/scala-2.12/daml-on-fabric.jar tmp
cp ./config-local-ci.yaml tmp/config-local.yaml
cp -r ../../../chaincode tmp
cp -r ./data/ledger* tmp/data
cp ./get_jwks_token.sh tmp
cp ./damlOnFabricStart.sh tmp
cp ./Dockerfile tmp
cd tmp
docker build . -t digitalasset/daml-on-fabric:2.2.0 --build-arg SDK_VERSION=${SDK_VERSION}
