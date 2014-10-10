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

package co.cask.tigon.apps.adbids;

import co.cask.tigon.api.annotation.Output;
import co.cask.tigon.api.annotation.ProcessInput;
import co.cask.tigon.api.common.Bytes;
import co.cask.tigon.api.flow.flowlet.Flowlet;
import co.cask.tigon.api.flow.flowlet.FlowletContext;
import co.cask.tigon.api.flow.flowlet.OutputEmitter;
import com.google.common.collect.Maps;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.NavigableMap;

/**
 * A {@link Flowlet} that notifies advertisers that a new user-id is available to be bid on.
 * In addition, it also provides advertisers relevant information needed to make a bid.
 */
public final class AdvertiserNotificationFlowlet extends AbstractBidProcessorFlowlet {

  @Output(Advertisers.TRAVEL)
  private OutputEmitter<IdData> travelOutputEmitter;

  @Output(Advertisers.SPORTS)
  private OutputEmitter<IdData> sportsOutputEmitter;

  @Output(Advertisers.MUSIC)
  private OutputEmitter<IdData> musicOutputEmitter;

  private Map<String, OutputEmitter> outputEmitters;

  @Override
  public void initialize(FlowletContext context) throws Exception {
    super.initialize(context);

    outputEmitters = Maps.newHashMap();
    outputEmitters.put(Advertisers.TRAVEL, travelOutputEmitter);
    outputEmitters.put(Advertisers.SPORTS, sportsOutputEmitter);
    outputEmitters.put(Advertisers.MUSIC, musicOutputEmitter);
  }

  @ProcessInput
  @SuppressWarnings("UnusedDeclaration")
  public void process(String id) throws Exception {
    if (distributedMode) {
      distributedProcess(id);
    } else {
      inMemoryProcess(id);
    }
  }

  private void distributedProcess(String id) throws Exception {
    int totalBids = 0;
    Result result = transactionAwareHTable.get(new Get(Bytes.toBytes(id)));
    if (!result.isEmpty()) {
      for (String advertiser : Advertisers.getAll()) {
        NavigableMap<byte[], byte[]> advertiserFamilyMap = result.getFamilyMap(Bytes.toBytes(advertiser));
        if (advertiserFamilyMap != null) {
          totalBids += advertiserFamilyMap.size();
        }
      }
    }

    for (String advertiser : Advertisers.getAll()) {
      if (!result.isEmpty() && result.getFamilyMap(Bytes.toBytes(advertiser)) != null) {
        outputEmitters.get(advertiser).emit(new IdData(id, result.getFamilyMap(Bytes.toBytes(advertiser)).size(),
                                                       totalBids));
      } else {
        outputEmitters.get(advertiser).emit(new IdData(id, 0, totalBids));
      }
    }
  }

  private void inMemoryProcess(String id) throws Exception {
    TABLE_LOCK.lock();
    try {
      if (!AD_BIDS.containsRow(id)) {
        for (Field item : Advertisers.class.getDeclaredFields()) {
          AD_BIDS.put(id, (String) item.get(null), 0);
        }
      }

      int totalBids = 0;
      Map<String, Integer> idItems = AD_BIDS.row(id);
      for (int advertiserBids : idItems.values()) {
        totalBids += advertiserBids;
      }

      for (String advertiser : Advertisers.getAll()) {
        outputEmitters.get(advertiser).emit(new IdData(id, AD_BIDS.get(id, advertiser), totalBids));
      }
    } finally {
      TABLE_LOCK.unlock();
    }
  }
}
