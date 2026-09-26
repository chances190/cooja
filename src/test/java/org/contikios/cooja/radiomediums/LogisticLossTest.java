package org.contikios.cooja.radiomediums;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/*
 * The medium has to place a transmitter at the distance the link budget gives
 * it, not at a distance an output-power indicator scales. TRANSMITTING_RANGE is
 * configured as the reference distance at TX_POWER_REFERENCE_DBM, so the range
 * at any other power follows from the log-distance model.
 */
class LogisticLossTest {
  /* Link budget constants, deliberately independent of the medium's fields. */
  private static final double PATH_LOSS_REFERENCE_DB = 40.19; /* Friis at 1 m, 2.44 GHz */
  private static final double RX_SENSITIVITY_DBM = -100.0;
  private static final double PATH_LOSS_EXPONENT = 3.0;
  private static final double REFERENCE_DBM = 0.0;
  private static final double DELTA = 1e-9;

  /* d0 at P, from the link budget alone: where the RSSI falls to sensitivity. */
  private static double linkBudgetRange(double pTxDbm) {
    return Math.pow(10.0, (pTxDbm - PATH_LOSS_REFERENCE_DB - RX_SENSITIVITY_DBM)
                          / (10.0 * PATH_LOSS_EXPONENT));
  }

  @Test
  void rangeFollowsTheLinkBudgetAtEveryCc2420Step() {
    double transmittingRange = linkBudgetRange(REFERENCE_DBM);
    double[] cc2420StepsDbm = {0.0, -1.0, -3.0, -5.0, -7.0, -10.0, -15.0, -25.0};
    for (double pTxDbm : cc2420StepsDbm) {
      assertEquals(linkBudgetRange(pTxDbm),
          LogisticLoss.referenceDistance(pTxDbm, transmittingRange,
                                         REFERENCE_DBM, PATH_LOSS_EXPONENT),
          DELTA, "range at " + pTxDbm + " dBm");
    }
  }
}
