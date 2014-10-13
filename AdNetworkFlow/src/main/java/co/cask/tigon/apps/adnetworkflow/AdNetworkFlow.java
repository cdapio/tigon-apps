/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.tigon.apps.adnetworkflow;


import co.cask.tigon.api.common.Bytes;
import co.cask.tigon.api.flow.Flow;
import co.cask.tigon.api.flow.FlowSpecification;
import co.cask.tigon.apps.adnetworkflow.advertisers.MusicAdvertiserFlowlet;
import co.cask.tigon.apps.adnetworkflow.advertisers.SportsAdvertiserFlowlet;
import co.cask.tigon.apps.adnetworkflow.advertisers.TravelAdvertiserFlowlet;

/**
 * An application for advertisers to bid on ads for user-views.
 *
 *<p>
 *This {@link Flow} accepts these runtime arguments:
 * <ul>
 *  <li><code>input.service.port</code>: Port to run the id input service on. Defaults to a random available port.</li>
 *
 *  <li><code>bids.output.file</code>: Path of file to log bids to.
 *    Defaults to only logging the output. (Specific to Standalone mode.)</li>
 *  <li>
 *    <code>write.to.hbase</code>: Configure the Flow to write and read bids from HBase. A new table is created in the
 *    HBase instance to track granted bids. This argument is compulsory. (Specific to Distributed mode.)
 *  </li>
 *  <li>
 *    <code>hbase.conf.path</code>: Path to HBase configuration file.
 *    This argument is optional. (Specific to Distributed mode.)
 *  </li>
 * </ul>
 *</p>
 *
 **/
public final class AdNetworkFlow implements Flow {
  public static final byte[] BID_TABLE_NAME = Bytes.toBytes("tigon.usecases.adnetwork");

  @Override
  public FlowSpecification configure() {
    return FlowSpecification.Builder.with()
      .setName("AdNetworkFlow")
      .setDescription("A sample application that demonstrates the implementation of a real time bidding ad network.")
      .withFlowlets()
      .add("id-collector", new UserIdInputFlowlet(), 1)
      .add("advertiser-notification", new AdvertiserNotificationFlowlet(), 1)
      .add("travel-advertiser", new TravelAdvertiserFlowlet(), 1)
      .add("sports-advertiser", new SportsAdvertiserFlowlet(), 1)
      .add("music-advertiser", new MusicAdvertiserFlowlet(), 1)
      .add("bid-resolver", new BidResolverFlowlet(), 1)
      .connect()
      .from("id-collector").to("advertiser-notification")
      .from("advertiser-notification").to("travel-advertiser")
      .from("advertiser-notification").to("sports-advertiser")
      .from("advertiser-notification").to("music-advertiser")
      .from("travel-advertiser").to("bid-resolver")
      .from("sports-advertiser").to("bid-resolver")
      .from("music-advertiser").to("bid-resolver")
      .build();
  }
}
