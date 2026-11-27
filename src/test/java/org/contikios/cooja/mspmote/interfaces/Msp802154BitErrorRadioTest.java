/*
 * Copyright (c) 2026, Valter Sanches Martins Neto
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.mspmote.interfaces;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/* The table is indexed by the margin over the sensitivity, so the interference
   has to come out of the signal and go into the noise. */
class Msp802154BitErrorRadioTest {
  private static final double SENSITIVITY_DBM = -95.0;

  @Test
  void testNoInterferenceLeavesTheMarginOverTheSensitivity() {
    assertEquals(10.0, Msp802154BitErrorRadio.signalToNoiseMargin(
        SENSITIVITY_DBM + 10.0, Double.NEGATIVE_INFINITY, SENSITIVITY_DBM), 1e-9);
  }

  @Test
  void testInterferenceEqualToTheSensitivityCostsThreeDb() {
    /* Total power 3 dB above the sensitivity: the margin is 3 dB below it. */
    double total = 10 * Math.log10(2 * Math.pow(10, SENSITIVITY_DBM / 10));
    assertEquals(-10 * Math.log10(2), Msp802154BitErrorRadio.signalToNoiseMargin(
        total, SENSITIVITY_DBM, SENSITIVITY_DBM), 1e-9);
  }
}
