#!/usr/bin/env bash

echo "switch to desired directory"
cd src/test/fixture || exit

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

echo "${DOCKERHUB_TOKEN}" | docker login -u "${DOCKERHUB_USERNAME}" --password-stdin

docker push digitalasset/daml-on-fabric:2.0.0-14-DEC-2020

echo "publish daml-on-fabric-chaincode image"
cd ../../../chaincode-image || exit
docker build . -t digitalasset/daml-on-fabric-chaincode:2.0.0-14-DEC-2020
docker push digitalasset/daml-on-fabric-chaincode:2.0.0-14-DEC-2020
docker logout