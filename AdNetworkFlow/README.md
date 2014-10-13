# AdNetworkFlow

``AdNetworkFlow`` example application for Tigon.

This application demonstrates how you could use Tigon to write a real-time bidding (RTB) advertisement framework.

## Implementation Details

The ``AdNetworkFlow`` application consists of:
 - A ``UserIdInputFlowlet`` that starts an HTTP server which accepts new user-ids for advertisers to bid on. In the
   realworld, this is most often implemented as a user cookie. 
 - An ``AdvertiserNotificationFlowlet`` that notifies each advertiser that a user-id can be bid on and 
   passes to advertisers information relevant to making a bid.
 - Multiple ``AdvertiserFlowlet``s that run custom algorithms to make bids on user views.
 - A ``BidResolverFlowlet`` that accepts bids from different advertisers for a particular user.
   It then grants the right to show a user an ad to the advertiser who bids the highest amount.
   
In a Standalone Runtime Environment, the granted bids are stored in an in-memory Table. In a Distributed Runtime
Environment, the granted bids are stored in HBase.

## Build & Usage
 
To build the ``AdNetworkFlow`` jar, run:

    mvn clean package

To build the jar without running the tests, run:

    mvn clean package -DskipTests

To run the app in the Standalone Runtime Environment:

    $ ./run_standalone.sh /path/to/AdNetworkFlow-<version>.jar co.cask.tigon.apps.adnetworkflow.AdNetworkFlow

This environment accepts an optional runtime argument:
 - ```bids.output.file``` Specify the file to log the granted bids to. By default, granted bids are logged only to
  the console.

To run the app in Distributed Runtime Environment:

    $ ./run_distributed.sh <ZookeeperQuorum> <HDFSNamespace>
    > START /path/to/AdNetworkFlow-<version>.jar co.cask.tigon.apps.adnetworkflow.AdNetworkFlow --write.to.hbase=true

This environment accepts an optional runtime argument:
 - ```hbase.conf.path``` Specify the path to the HBase configuration. By default, the configuration file in the
  classpath is used.

Both runtimes support an additional argument:
- ```input.service.port``` Specify the port on which the server started by ``UserIdInputFlowlet`` runs. By default, the 
  server runs on a random available port.

To notify the app that a user-view can be bid on, make an HTTP request to the server started by ``UserIdInputFlowlet``.

    POST http://<host>:<port>/id/<user-id>

The granted bids are logged in the format:

    Bid for <user-id> granted to <advertiser-name> for $<bid-amount>.

## Example Usage

Make a request to the ``UserIdInputFlowlet``, notifying it that the views of the user ``testuser`` can be bid on.

    $ curl -v -X POST http://localhost:47989/id/testuser

The bid granted for this user is logged as:

    Bid for testuser granted to MUSIC advertiser for $15.

## License and Trademarks

Copyright © 2014 Cask Data, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
