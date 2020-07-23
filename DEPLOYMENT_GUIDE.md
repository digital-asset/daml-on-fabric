## Deploying DAML on Fabric

### What We're Doing

1. Setting up our DAML App, in this case a project called `create-daml-app`
2. Building and running a local Fabric ledger with DAML support through the `daml-on-fabric` project
3. Deploying our `create-daml-app` code to our Fabric ledger where it will record its state
4. Running a JSON endpoint that automatically creates every endpoint we need for our DAML application
5. Starting up a React UI that will consume these JSON endpoints with no lower level interaction with DAML or Fabric necessary

### Building Our DAML App

1. Let's `daml create-daml-app my-app` to create our example application in the `my-app` directory.
2. Now follow the build instructions in `my-app/README.md`. Build the project up to and including the `yarn build` command, then come back here

### Starting up Fabric

Now that we have our DAML app built it needs a place to run, let's grab `daml-on-fabric` and get it running

3. Simply `git clone https://github.com/digital-asset/daml-on-fabric.git` and follow the [instructions](https://github.com/digital-asset/daml-on-fabric) for "Running a local Hyperledger Fabric network"

	Make sure for Java, Scala, and SBT that you are using the exact versions specified or otherwise you may encounter build or runtime issues. You can use sdkman.io to easily install these specific versions and manage multiple versions.

4. From the root `daml-on-fabric` directory run `sbt "run --port 6865 --role provision,time,ledger" -J-DfabricConfigFile=config-local.yaml` which will let the DAML runtime start talking to our Fabric instance.

	Give this process a moment to start up, it is ready once you see output like:
	
    ```
    13:57:06.404 INFO  c.d.p.a.ApiServices - DAML LF Engine supports LF versions: 0, 0.dev, 1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.dev; Transaction versions: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10; Value versions: 1, 2, 3, 4, 5, 6, 7
    13:57:11.307 INFO  c.d.p.a.s.LedgerConfigProvider - No ledger configuration found, submitting an initial configuration 4f751996-52ab-44fb-b218-effb74b88fe6
    13:57:11.354 INFO  c.d.p.a.s.LedgerConfigProvider - Initial configuration submission 4f751996-52ab-44fb-b218-effb74b88fe6 was successful
    Received 2 tx proposal responses. Successful+verified: 2 . Failed: 0  - Fcn: RawBatchWrite 
    13:57:11.793 INFO  c.d.p.a.s.LedgerConfigProvider - New ledger configuration Configuration(1,TimeModel(PT0S,PT2M,PT2M),PT24H) found at Absolute(00000000000000010000000000000000)
    13:57:11.931 INFO  c.d.p.a.LedgerApiServer - Listening on 0.0.0.0:6865 over plain text.
    13:57:11.934 INFO  c.d.p.a.StandaloneApiServer - Initialized API server version 1.2.0 with ledger-id = fabric-ledger, port = 6865, dar file = List()
    ```

### Deploying Our DAML App

Alright our Fabric instance is up and running, time to deploy our application and give it a JSON endpoint for our user facing UI

5. From the `my-app` directory run `daml deploy --host localhost --port 6865`. This will deploy your DAR file (ie. DAML application) to your Fabric ledger.
6. From the same directory run `daml json-api --ledger-host localhost --ledger-port 6865 --http-port 7575` to automatically create a json api that will connect to your Fabric ledger and serve up a JSON endpoint for your UI to use

### Setting up Our Frontend

Alright our backend is all up and running. Now let's start up our UI

7. Set our ledger id with `export REACT_APP_LEDGER_ID=fabric-ledger` so our UI knows the id of the ledger to use. Alternativel you can place this in a `.env` file in the root directory of `my-app`
8. Start the ui by running `yarn start` from the `my-app/ui` directory
9. The UI should start up a browser window by itself once ready, if it doesn’t go to http://localhost:3000/

	It may take a few moments for the website to load as yarn starts the server before the UI is built

10. Login as `Alice`, `Bob`, or `Charlie` and try out `create-daml-app`

	Due to some initialization steps the first time you login may take a few seconds, this delay is not present on subsequent logins.

Congratulations, you’ve successfully deployed your first DAML app to a live Fabric ledger!
