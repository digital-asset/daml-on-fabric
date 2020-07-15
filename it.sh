#!/usr/bin/env bash
# Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

################################################################################

set -euo pipefail

# NOTE: This file is used by `make it` in a context where the example ledger
# server has already been built. It is not intended to be used directly.
echo "switch to desired directory"
cd src/test/fixture

export SDK_VERSION=$(cat ../../../build.sbt| egrep -o "sdkVersion.*=.*\".*\"" | perl -pe 's|sdkVersion.*?=.*?"(.*?)"|\1|')

echo "Downloading ledger test tool"
./download_test_tool_extract_dars.sh

echo "Cleaning tmp folder"
rm -rf ./tmp/*

pwd
echo "Generate Fabric network config"
./gen.sh

echo "Building CI Docker image"
./build_ci.sh

function compress_dir() {
  tar  -C "$1" -czvvf - . | base64
}

# This is specifically for CircleCI:
# It does not allow us to attach volume from host machine, like usually configured for Fabric.
# Thus we just send whole configuration directory this way.

echo "Compressing MSP directory for Fabric..."
CONFIGTX="$(compress_dir ./tmp/data/)"
export CONFIGTX=${CONFIGTX}

echo "Launching Fabric network and DAML-on-Fabric server with Authentication"

declare -a auths_certs=(\
"--auth-jwt-rs256-crt=./data/ledger_certs/rs256_jwt_token.crt" \
"--auth-jwt-es256-crt=./data/ledger_certs/es256_jwt_token.crt" \
"--auth-jwt-es512-crt=./data/ledger_certs/es512_jwt_token.crt" \
"--auth-jwt-hs256-unsafe=someunsafesecret" \
"--auth-jwt-rs256-jwks=http://jwt-provider:8088")
declare -a auths_tokens=(\
"--access-token-file=./data/ledger_tokens/rs256_jwt_token" \
"--access-token-file=./data/ledger_tokens/es256_jwt_token" \
"--access-token-file=./data/ledger_tokens/es512_jwt_token" \
"--access-token-file=./data/ledger_tokens/hs256_jwt_token" \
"--access-token-file=./data/ledger_tokens/jwks_jwt_token")
declare -a party_users=(\
"Alice" \
"Bob" \
"Bank_A" \
"Bank_B" \
"Charlie")

LEDGER_AUTH=""
LEDGER_TOKEN=""
PARTY_USER=""
ALLOCATE_PARTIES_COMMAND=""

export DOCKER_COMPOSE_FILE=docker-compose-ci.yaml
export DOCKER_NETWORK=damlonfabric_ci

./fabric.sh down
./fabric.sh clean
./fabric.sh upNetworkDetached


./fabric.sh upDamlOnFabricDetached

sleep 150s

echo "Launching the test tool..."

export TEST_COMMAND="/usr/local/openjdk-8/bin/java -jar ledger-api-test-tool.jar \
localhost:12222 \
--timeout-scale-factor=6 \
--concurrent-test-runs=2 \
--verbose"
docker exec -it damlonfabric_ci_daml-on-fabric-2_1 ${TEST_COMMAND}

echo "Launching the auth tests..."

for i in "${!auths_certs[@]}"
do

  export LEDGER_AUTH="${auths_certs[i]}"
  export LEDGER_TOKEN="${auths_tokens[i]}"
  export PARTY_USER="${party_users[i]}"

  ./fabric.sh upDamlOnFabricDetached

  echo "Giving time for everything to initialize with configuration ${LEDGER_AUTH}"

  sleep 120s

  if [[ "$LEDGER_AUTH" = "--auth-jwt-rs256-jwks=http://jwt-provider:8088" ]]; then
    docker run -d -p 8088:8088 --name=jwt-provider --network damlonfabric_ci_default brandwatch/jwks-jwt-provider
    sleep 10
    docker exec -it damlonfabric_ci_daml-on-fabric-2_1 bash get_jwks_token.sh
  fi

  export ALLOCATE_PARTIES_COMMAND="daml ledger allocate-parties --host localhost --port 12222 $LEDGER_TOKEN $PARTY_USER"
  echo "Launching the allocate-parties command... ${ALLOCATE_PARTIES_COMMAND}"
  docker exec -it damlonfabric_ci_daml-on-fabric-2_1 ${ALLOCATE_PARTIES_COMMAND}

#  export TEST_GRPC_COMMAND="grpcurl localhost:12222 list; grpcurl -plaintext localhost:12222 com.daml.ledger.api.v1.admin.PartyManagementService/AllocateParty; grpcurl -plaintext localhost:12222 com.daml.ledger.api.v1.admin.PartyManagementService/ListKnownParties"

done

sleep 5
docker rm -f jwt-provider

echo "Completed Auth tests"


echo "Test tool run is complete."
