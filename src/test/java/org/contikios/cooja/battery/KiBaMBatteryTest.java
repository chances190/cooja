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
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.contikios.cooja.battery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KiBaMBatteryTest {
  private static final double DELTA = 1e-6;

  @Test
  void noLoadPreservesCharge() {
    var battery = new KiBaMBattery(1, 1, 0.1, 0.5, 3.0, 2.0);
    battery.update(0, 24);
    assertEquals(1.0, battery.stateOfCharge(), DELTA);
    assertEquals(3.0, battery.voltage(), DELTA);
    assertFalse(battery.isDead());
  }

  @Test
  void loadReducesCharge() {
    var battery = new KiBaMBattery(1, 1, 0.1, 0.5, 3.0, 2.0);
    battery.update(0.1, 1);
    assertTrue(battery.stateOfCharge() < 1.0);
    assertTrue(battery.stateOfCharge() > 0.8);
    assertTrue(battery.voltage() < 3.0);
    assertFalse(battery.isDead());
  }

  @Test
  void repeatedLoadEventuallyDepletes() {
    var battery = new KiBaMBattery(0.01, 1, 0.1, 0.5, 3.0, 2.0);
    for (int i = 0; i < 1000 && !battery.isDead(); i++) {
      battery.update(0.1, 0.01);
    }
    assertTrue(battery.isDead());
    assertEquals(0, battery.stateOfCharge(), DELTA);
  }

  @Test
  void recoveryRaisesAvailableCharge() {
    var battery = new KiBaMBattery(1, 1, 0.1, 0.5, 3.0, 2.0);
    battery.update(0.5, 0.5);
    double afterLoad = battery.voltage();
    battery.update(0, 1);
    assertTrue(battery.voltage() > afterLoad);
  }
}
