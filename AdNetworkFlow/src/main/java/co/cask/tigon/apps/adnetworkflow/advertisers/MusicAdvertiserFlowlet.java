package co.cask.tigon.apps.adnetworkflow.advertisers;

import co.cask.tigon.api.annotation.ProcessInput;
import co.cask.tigon.api.flow.flowlet.AbstractFlowlet;
import co.cask.tigon.api.flow.flowlet.OutputEmitter;
import co.cask.tigon.apps.adnetworkflow.Advertisers;
import co.cask.tigon.apps.adnetworkflow.Bid;
import co.cask.tigon.apps.adnetworkflow.IdData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An advertiser that makes bids on users to show ads related to music.
 */
public class MusicAdvertiserFlowlet extends AbstractFlowlet {
  private static final Logger LOG = LoggerFactory.getLogger(MusicAdvertiserFlowlet.class);
  private OutputEmitter<Bid> bidOutputEmitter;

  /**
   * Compute the MusicAdvertiser's bid amount for each incoming user id.
   * @param idData idData used to compute the bid amount.
   * @throws Exception
   */
  @ProcessInput(Advertisers.MUSIC)
  @SuppressWarnings("UnusedDeclaration")
  public void process(IdData idData) throws Exception {
    double bidAmount;
    if (idData.getTotalCount() == 0) {
      bidAmount = 15;
    } else {
      bidAmount = ((idData.getTotalCount() - idData.getItemCount()) / (2 * (double) idData.getTotalCount())) * 10;
      bidAmount = Math.sin(Math.PI * bidAmount);
    }
    bidOutputEmitter.emit(new Bid(idData.getId(), Advertisers.MUSIC, bidAmount), "userId", idData.getId());
    LOG.info("Bid {} for user {}", bidAmount, idData.getId());
  }
}
