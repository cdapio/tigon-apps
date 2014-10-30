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

package co.cask.tigon.apps.clickStream;

import co.cask.tigon.api.annotation.Output;
import co.cask.tigon.api.annotation.ProcessInput;
import co.cask.tigon.api.flow.Flow;
import co.cask.tigon.api.flow.FlowSpecification;
import co.cask.tigon.api.flow.flowlet.AbstractFlowlet;
import co.cask.tigon.api.flow.flowlet.FlowletContext;
import co.cask.tigon.api.flow.flowlet.OutputEmitter;
import co.cask.tigon.sql.flowlet.AbstractInputFlowlet;
import co.cask.tigon.sql.flowlet.GDATFieldType;
import co.cask.tigon.sql.flowlet.GDATSlidingWindowAttribute;
import co.cask.tigon.sql.flowlet.StreamSchema;
import co.cask.tigon.sql.flowlet.annotation.QueryOutput;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link co.cask.tigon.api.flow.Flow} serves as a demonstration for using Tigon SQL to join 2 data streams.
 * In this example a view event data stream and a click event data stream are joined to generate meta information for
 * each click event using the view even data stream. Some click events are filtered out on the basis of conditions
 * specified in the SQL query.
 */
public class ClickStreamFlow implements Flow {
  @Override
  public FlowSpecification configure() {
    return FlowSpecification.Builder.with()
      .setName("ClickStreamFlow")
      .setDescription("A sample flow application that collates data from a stream of view and click events")
      .withFlowlets()
      .add("SQLInput", new SQLInputFlowlet())
      .add("Digest", new DigestFlowlet())
      .connect()
      .from("SQLInput").to("Digest")
      .build();
  }
}

class ClickInfo {
  private static final Gson GSON = new Gson();

  int time;
  String referrerPageInfo;
  int linkID;
  String linkDetails;
  int refID;

  public ClickInfo(int time, String referrerPageInfo, int linkID, String linkDetails) {
    this.time = time;
    this.referrerPageInfo = referrerPageInfo;
    this.linkID = linkID;
    this.linkDetails = linkDetails;
  }

  public String toString() {
    return "ClickTime : " + time + "\tLink Message : " + getLinkName() + "\tReferrer Page : " + referrerPageInfo;
  }

  public String getLinkName() {
    return GSON.fromJson("{" + linkDetails.replace("-", ",") + "}", JsonObject.class)
      .get(Integer.toString(linkID)).getAsString();
  }
}

class SQLInputFlowlet extends AbstractInputFlowlet {
  private static final Logger LOG = LoggerFactory.getLogger(SQLInputFlowlet.class);

  @Override
  public void create() {
    setName("SQLinputFlowlet");
    setDescription("A sample application that joins view stream and click stream data to extract all information " +
                     "associated with a click event");
    StreamSchema viewSchema = new StreamSchema.Builder()
      .setName("viewData")
      .addField("pageViewID", GDATFieldType.INT, GDATSlidingWindowAttribute.INCREASING)
      .addField("viewTime", GDATFieldType.INT)
      .addField("lid1", GDATFieldType.INT)
      .addField("lid2", GDATFieldType.INT)
      .addField("lid3", GDATFieldType.INT)
      .addField("pageInfo", GDATFieldType.STRING)
      .addField("linkDetails", GDATFieldType.STRING)
      .build();
    StreamSchema clickSchema = new StreamSchema.Builder()
      .setName("clickData")
      .addField("refpageViewID", GDATFieldType.INT, GDATSlidingWindowAttribute.INCREASING)
      .addField("clickTime", GDATFieldType.INT)
      .addField("lid", GDATFieldType.INT)
      .build();
    addJSONInput("viewStream", viewSchema);
    addJSONInput("clickStream", clickSchema);
    // Fetches meta-information for all click events using the related view event. Click events which occur outside a
    // 5 minute (300 seconds) window of the view event are filtered out.
    addQuery("clickDataStream",
             "SELECT clickTime as time, pageInfo as referrerPageInfo, lid as linkID, " +
               "linkDetails, refpageViewID as refID " +
               "INNER_JOIN FROM viewStream.viewData v, clickStream.clickData c WHERE v.pageViewID = c.refpageViewID " +
               "AND clickTime >= viewTime AND clickTime <= (300 + viewTime)" +
               " AND (c.lid = v.lid1 or c.lid = v.lid2 or c.lid = v.lid3)");
  }

  @Output("clickInfo")
  private OutputEmitter<ClickInfo> emitter;

  @QueryOutput("clickDataStream")
  void process(ClickInfo obj) {
    LOG.info("Emitting Data " + obj.toString());
    emitter.emit(obj);
  }
}

class DigestFlowlet extends AbstractFlowlet {
  private static final Logger LOG = LoggerFactory.getLogger(DigestFlowlet.class);

  private String pingURL;
  // TODO eliminate org.apache.http dependency TIGON-5
  private HttpClient httpClient;

  @Override
  public void initialize(FlowletContext context) throws Exception {
    try {
      pingURL = context.getRuntimeArguments().get("baseURL");
      if (pingURL != null) {
        httpClient = new DefaultHttpClient();
      }
    } catch (Exception e) {
      // no-op
    }
  }

  /**
   * This method is invoked for each {@link ClickInfo} object emitted by the {@link SQLInputFlowlet}.
   * It pings a string encoded JSON of the incoming data object to the provided baseURL.
   * @param obj
   */
  @ProcessInput("clickInfo")
  void process(ClickInfo obj) {
    LOG.info("Received Click Info - " + obj.toString());
    if (httpClient != null) {
      try {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("time", obj.time);
        bodyJson.addProperty("referrerPageInfo", obj.referrerPageInfo);
        bodyJson.addProperty("linkID", obj.linkID);
        bodyJson.addProperty("linkDetails", obj.linkDetails);
        bodyJson.addProperty("refID", obj.refID);
        HttpPost httpPost = new HttpPost(pingURL);
        StringEntity params = new StringEntity(bodyJson.toString(), Charsets.UTF_8);
        httpPost.addHeader("Content-Type", "application/json");
        httpPost.setEntity(params);
        EntityUtils.consumeQuietly(httpClient.execute(httpPost).getEntity());
      } catch (Exception e) {
        Throwables.propagate(e);
      }
    }
  }
}

