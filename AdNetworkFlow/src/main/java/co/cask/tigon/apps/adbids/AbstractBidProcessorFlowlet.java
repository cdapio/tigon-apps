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

import co.cask.tephra.hbase96.TransactionAwareHTable;
import co.cask.tigon.api.common.Bytes;
import co.cask.tigon.api.flow.flowlet.AbstractFlowlet;
import co.cask.tigon.api.flow.flowlet.Flowlet;
import co.cask.tigon.api.flow.flowlet.FlowletContext;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableExistsException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An abstract {@link Flowlet} that encapsulates the resources needed to access and process bids.
 * In-memory implementations should use the underlying {@link Table} to maintain state. Similarly, distributed
 * implementations must use the {@link TransactionAwareHTable}.
 */
public abstract class AbstractBidProcessorFlowlet extends AbstractFlowlet {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractBidProcessorFlowlet.class);
  protected static final Lock TABLE_LOCK = new ReentrantLock();
  protected static final Table<String, String, Integer> AD_BIDS = HashBasedTable.create();
  protected File outputFile;

  protected boolean distributedMode;
  protected TransactionAwareHTable transactionAwareHTable;

  @Override
  public void initialize(FlowletContext context) throws Exception {
    super.initialize(context);
    String outputFilePath = context.getRuntimeArguments().get("bids.output.file");
    String hbaseConfFilePath = context.getRuntimeArguments().get("hbase.conf.path");
    if (outputFilePath != null) {
      outputFile = new File(outputFilePath);
    }
    if (hbaseConfFilePath != null) {
      distributedMode = true;
      File hbaseConf = new File(hbaseConfFilePath);
      Configuration configuration = new Configuration(true);
      configuration.addResource(hbaseConf.toURI().toURL());

      HBaseAdmin hBaseAdmin = new HBaseAdmin(configuration);
      HTableDescriptor hTableDescriptor = new HTableDescriptor(AdNetworkFlow.BID_TABLE_NAME);
      for (String advertiser : Advertisers.getAll()) {
        hTableDescriptor.addFamily(new HColumnDescriptor(Bytes.toBytes(advertiser)));
      }
      createTableIfNotExists(hBaseAdmin, AdNetworkFlow.BID_TABLE_NAME, hTableDescriptor);

      if (!hBaseAdmin.isTableEnabled(AdNetworkFlow.BID_TABLE_NAME)) {
        hBaseAdmin.enableTable(AdNetworkFlow.BID_TABLE_NAME);
      }

      hBaseAdmin.close();
      transactionAwareHTable = new TransactionAwareHTable(new HTable(configuration, AdNetworkFlow.BID_TABLE_NAME));
      context.addTransactionAware(transactionAwareHTable);
    }
  }

  private void createTableIfNotExists(HBaseAdmin admin, byte[] tableName,
                                     HTableDescriptor tableDescriptor) throws IOException {
    long timeout = 5000L;
    TimeUnit timeoutUnit = TimeUnit.MILLISECONDS;
    if (admin.tableExists(tableName)) {
      return;
    }

    String tableNameString = Bytes.toString(tableName);

    try {
      LOG.info("Creating table '{}'", tableNameString);
      admin.createTable(tableDescriptor);
      LOG.info("Table created '{}'", tableNameString);
      return;
    } catch (TableExistsException e) {
      // table may exist because someone else is creating it at the same
      // time. But it may not be available yet, and opening it might fail.
      LOG.info("Failed to create table '{}'. {}.", tableNameString, e.getMessage(), e);
    }

    // Wait for table to materialize
    try {
      Stopwatch stopwatch = new Stopwatch();
      stopwatch.start();
      long sleepTime = timeoutUnit.toNanos(timeout) / 10;
      sleepTime = sleepTime <= 0 ? 1 : sleepTime;
      do {
        if (admin.tableExists(tableName)) {
          LOG.info("Table '{}' exists now. Assuming that another process concurrently created it.", tableName);
          return;
        } else {
          TimeUnit.NANOSECONDS.sleep(sleepTime);
        }
      } while (stopwatch.elapsedTime(timeoutUnit) < timeout);
    } catch (InterruptedException e) {
      LOG.warn("Sleeping thread interrupted.");
    }
    LOG.error("Table '{}' does not exist after waiting {} ms. Giving up.", tableName, timeout);
  }

}
