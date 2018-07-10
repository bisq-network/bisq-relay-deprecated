bisq-relay
========


Overview
--------

Relay node for mobile notification app.
Messages get sent from the user's Bisq app to the relay node over Tor to a http server. The relay node forwards the message using the Apple and Google notification certificates to the mobile Bisq app. It requires to have both Apple and Google certificates added to the resource directory.


Prerequisites for running a relay
--------

To run a relay, you will need:

  - JDK 8 if you want to build and run a node locally.
  - The `tor` binary (e.g. `brew install tor`) if you want to run a hidden service locally.


How to deploy locally
--------

### Build

    ./gradlew assemble

### Run

    java -jar ./build/libs/bisq-relay.jar


### Run as Tor hidden service

With your relay running at localhost:8080, run:

    tor -f torrc
        
Wait for the process to report that it is "100% bootstrapped", then copy your newly-generated .onion address:

    export MR_ONION=$(cat build/tor-hidden-service/hostname)

Test the endpoints of your hidden service via curl with the --socks5-proxy option:

    curl --socks5-hostname 127.0.0.1:9050 http://$MR_ONION/getAllMarketPrices

