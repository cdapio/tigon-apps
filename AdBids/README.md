# AdBids

``AdBids`` example application for Tigon.

This application demonstrates how you could use Tigon to write a realtime advertisement bidding framework.

## Implementation Details

The ``AdBids`` application is primarily composed of:
 - An ``UserIdInputFlowlet`` that starts a HTTP server which accepts new user-ids for advertisers to bid on.
 - An ``AdvertiserNotificationFlowlet`` that notifies each advertiser that a user-id can be bid on and passes to them
   relevant information to make a bid.
 - Multiple ``AdvertiserFlowlet``s that run custom algorithms to make bids on user views.
 - A ``GrantBidsFlowlet`` that accepts bids from different advertisers for a particular user.
   It then grants the right to show an ad to that user to the advertiser who bids the largest amount.
   
In a Standalone Runtime Environment the granted bids are stored in an in-memory Table. In a Distributed Runtime
Environment the granted bids are stored in HBase.

## Build & Usage
 
To build ``AdBids`` jar, run:

 ```mvn clean package```

To build the jar without running the tests, run:

```mvn clean package -DskipTests```

To run the app in Standalone Runtime Environment:

 ```
 $ ./run_standalone.sh /path/to/AdBids-<version>.jar co.cask.tigon.apps.adbids.AdBids
 ```

This environment accepts an optional runtime argument:
 - ```bids.output.file``` Specify the file to log the granted bids to. By default granted bids are logged only to
  the console.

To run the app in Distributed Runtime Environment:
 ```
 $ ./run_distributed.sh <ZookeeperQuorum> <HDFSNamespace>
 > START /path/to/AdBids-<version>.jar co.cask.tigon.apps.adbids.AdBids --hbase.conf.path=<path-to-hbase-configuration>
 ```

Both runtimes support an additional argument:
- ```input.service.port``` Specify the port on which the server started by ``UserIdInputFlowlet`` runs. By default the 
  server runs on a random available port.

To notify the app that a user-view can be bid on, make an HTTP request to the server started by ``UserIdInputFlowlet``.

```
  POST http://<host>:<port>/id/<user-id>
```

The granted bids are logged in the following format:

```
  Bid for <user-id> granted to <advertiser-name> for $<bid-amount>.
```

## Example Usage

Make a request to the ``UserIdInputFlowlet`` notifying it that a user ``testuser``'s view can be bid on.

```
 $ curl -v -X POST http://localhost:47989/id/testuser
```

The bid granted for this user is logged as:

```
  Bid for testuser granted to TRAVEL advertiser for $12.
```

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
