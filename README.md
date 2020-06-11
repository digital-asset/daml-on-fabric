
[![CircleCI](https://circleci.com/gh/digital-asset/daml-on-fabric.svg?style=svg&circle-token=a00ee8cfc2d112608361c5698f62fdbf208aea30)](https://circleci.com/gh/digital-asset/daml-on-fabric) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/digital-asset/daml-on-fabric/blob/master/LICENSE)


# DAML on Fabric

Ledger implementation enabling DAML applications to run on Hyperledger Fabric 2.x

# Quick Start Guide

## Prerequisites

These are the minimal requirements that this flow was tested with.
Docker and Docker-Compose are required to run a Hyperledger Fabric network, and greater versions of docker generally can be used. Note: running Docker as root is not recommended.

- **docker-compose** version 1.24.0 or greater
- **docker CE** version 18.09.6 or greater
- **Java / JDK** version 1.8.0.x
- **DAML SDK** version 1.2.0
- **Hyperledger Fabric Tools** version 2.0.0 or greater

You can get the latest Hyperledger Fabric Tools by running the following command, and binaries will be downloaded to  <download_directory>/bin

```
cd <download_directory>
curl -sSL https://bit.ly/2ysbOFE | bash -s -- 2.1.0 -s
```

Now add the <download_directory>/bin to your PATH so that the fabric tools are available for use

```
export PATH=<download_directory>/bin:$PATH
```

## Cloning DAML-on-Fabric

For the sake of cleanliness, we are using the directory `~/daml-on-fabric` as the root for the repository.

```
cd
git clone https://github.com/digital-asset/daml-on-fabric.git
```

## Running a local Hyperledger Fabric network

This is achieved by running a Docker-Compose network which is a typical way to set up Fabric for development environments. 
Our particular example sets up a network of 2 endorsing peers.

```
cd ~/daml-on-fabric/src/test/fixture/
./gen.sh
./restart_fabric.sh
```

The script used is a shortcut for `./fabric.sh down && ./fabric.sh up` that will erase all current data and start a fresh network.

## Deploying a DAML Application to Fabric
Check out the [deployment guide](DEPLOYMENT_GUIDE.md) for a demonstration on deploying a full E2E application.

## DAML-on-Fabric command line and Services

The basic command to run the ledger right from the repository is like this:

```
sbt "run --role <roles> [--port NNNN] [DAMLArchive.dar DAMLArchive2.dar ...]"
```

**Important: the ledger will connect to a Fabric network specified in *config-local.yaml* file.**

If you want to run the ledger against something other than the local Fabric test network, you should specify the configuration of the network by providing the additional `fabricConfigFile` argument, as shown below: 

```
sbt "run ..." -J-DfabricConfigFile=<configuration file>
```

By default, it will use "config-local.yaml", which you can use for reference.

#### Services, or "Roles"

You may have noticed that the required argument for DAML-on-Fabric is "role".

There are several roles that define which parts of the service are going to be executed:

- `provision`
   - will connect to Fabric and prepare it to be used as storage for a DAML ledger.
- `ledger`
   - will run a DAML Ledger API endpoint at a port specified with `--port` argument.
- `time`
   - will run a DAML time service, which writes heartbeats to the ledger. There should be exactly one time service per ledger.
- `explorer`
   - will run a block explorer service that exposes a REST API to view the content of blocks and transactions inside the Fabric network for debugging or demonstration purposes. The explorer will run at a port specified in *config-local.yaml* file. It provides REST API that responds at endpoints `/blocks[?id=...]` and `/transactions[?id=...]`

One ledger may perform multiple roles at the same time, in which case roles are comma-separated. 

## Running Java Quick Start against DAML-on-Fabric

### Step 1. Start Hyperledger Fabric

```
cd ~/daml-on-fabric/src/test/fixture/
./gen.sh
./restart_fabric.sh
```

### Step 2. Extract and build Quick Start project 

```
cd
rm -rf quickstart
daml new quickstart quickstart-java
```
Created a new project in "quickstart" based on the template "quickstart-java".


```
cd quickstart
daml build
```
Compiling daml/Main.daml to a DAR
Created .daml/dist/quickstart-0.0.1.dar


### Step 3. Run the Ledger with Quick Start DAR archive

```
cd ~/daml-on-fabric/
sbt "run --port 6865 --role provision,time,ledger,explorer ../quickstart/.daml/dist/quickstart-0.0.1.dar"
```

### Step 4. Allocate parties on the ledger

There's no automatic mapping of DAML parties to parties on the Fabric ledger. We'll need to create the parties on Fabric that match the party names in DAML:

```
cd ~/quickstart/
daml ledger allocate-parties --host localhost --port 6865 Alice Bob Carol USD_Bank EUR_Bank
```

### Step 5. Run DAML Navigator

```
cd ~/quickstart/
daml navigator server localhost 6865 --port 4000
```

### Step 6. Conclusion

Now you can explore your freshly setup DAML ledger.

You should have the following services running:

- DAML Navigator at http://localhost:4000/
- Hyperledger Fabric block explorer at http://localhost:8080/blocks and http://localhost:8080/transactions
- DAML Ledger API endpoint at *localhost:6865*
- Fabric Orderer and Peers running at ports as defined in docker compose file

More information on Quick Start example and DAML in general can be found here:

https://docs.daml.com/getting-started/quickstart.html

### Step 7. Upload own dar files 

To upload custom dars run the following command where the custom dar is located:

```
daml ledger upload-dar <dar-file-location> --host localhost --port 6865
```


### Step 8. Run Ledger API Test Tool 

For testing purposes, the Ledger API provides a tool to execute some tests against the deployed network. 
You can find the ledger-api-test-tool.jar in the src/test/fixture folder. 
This JAR is generated when the make it command


```
cd ~/daml-on-fabric/src/test/fixture
./download_test_tool_extract_dars.sh
ls *.dar | xargs -I {} daml ledger upload-dar {} --host localhost --port 6865
java -jar ledger-api-test-tool.jar --all-tests localhost:6865 --timeout-scale-factor=10
```

**NOTE**: 
The localhost and port can be changed to accommodate the projects needs


### Step 9. Run with authentication

We can also run the Ledger API with authentication enabled. The ledger API uses JWT for authentication. There are a few methods that are supported:
    * rsa256
    * esda256
    * esda512
    * hs256  (this is **unsafe**)
    * rsa256 jwks
    
To enable authentication, follow one of the examples below.

To run with rsa256 jwt authentication enabled:

```
sbt "run --port 6865 --role provision,time,ledger,explorer ./quickstart/.daml/dist/quickstart-0.0.1.dar --auth-jwt-rs256-crt=<path-to-crt-file>"
```

To run with esda256 jwt authentication enabled:

```
sbt "run --port 6865 --role provision,time,ledger,explorer ./quickstart/.daml/dist/quickstart-0.0.1.dar --auth-jwt-es256-crt=<path-to-crt-file>"
```

To run with esda512 jwt authentication enabled:

```
sbt "run --port 6865 --role provision,time,ledger,explorer ./quickstart/.daml/dist/quickstart-0.0.1.dar --auth-jwt-es512-crt=<path-to-crt-file>
```

To run with hs256 jwt authentication enabled:

```
sbt "run --port 6865 --role provision,time,ledger,explorer ./quickstart/.daml/dist/quickstart-0.0.1.dar --auth-jwt-hs256-unsafe=<path-to-crt-file>"
```

To run with rsa256 jwks jwt authentication enabled:

```
sbt "run --port 6865 --role provision,time,ledger,explorer ./quickstart/.daml/dist/quickstart-0.0.1.dar --auth-jwt-rs256-jwkse=<URI-to-jwks-service>"
```

If authentication is enabled, then all the calls to the Ledger API will need to be authenticated. Given the the authentication, please use the following:

To run daml commands for Legder API with any jwt authentication enabled

```
daml ledger allocate-parties --host localhost --port 6865 Alice Bob Carol USD_Bank EUR_Bank --access-token-file=<path-to-token>
```


A few examples for this type of authentication can be found in **src/test/fixture/data/ledger_certs** and **src/test/fixture/data/ledger_tokens**


For more information, regarding authentication on the Ledger API and token generation, can be found [here](https://docs.daml.com/tools/sandbox.html#running-with-authentication) and [here](https://docs.daml.com/app-dev/authentication.html) ,  respectively.


### Step 10. Running with CI

This project has its own CI environment that builds, spins up the network, and runs the ledger tests and the authentication tests. To replicate the same steps as are run in CI locally, you can run this command:

```
$ make it
```

This will clean, compile, build and package the code, deploy the network and run all the necessary tests.

### Step 11.  Running a Multi-node Setup
## Start Fabric Network

```
cd src/test/fixture && ./restart_fabric.sh
```

## Output DAR from test tool

```
cd src/test/fixture && ./download_test_tool_extract_dars.sh
```

## First Participant Node
```
sbt "run --role ledger,time,provision --port 11111" -J-DfabricConfigFile=config-local.yaml
```

## Second Participant Node
```
sbt "run --role ledger --port 12222" -J-DfabricConfigFile=config-local.yaml
```

## Third Participant Node
```
sbt "run --role ledger --port 13333 src/test/fixture/SemanticTests.dar src/test/fixture/Test-stable.dar" -J-DfabricConfigFile=config-local.yaml
```

## Run Ledger Test Tool against all nodes
```
java -jar ledger-api-test-tool.jar localhost:11111 --include=SemanticTests --timeout-scale-factor 2.0
java -jar ledger-api-test-tool.jar localhost:12222 --include=SemanticTests --timeout-scale-factor 2.0
java -jar ledger-api-test-tool.jar localhost:13333 --include=SemanticTests --timeout-scale-factor 2.0
```


# Technical README

If there is a need for further customization, there are a few options that can be changed.
Please refer to this [README](./src/test/fixture/README.md) for further information.

# Architecture Guide
A more detailed description of the technical architecture can be found in [Architecture Guide](./docs/DAML_on_Fabric_v2_Architecture.pdf) 
