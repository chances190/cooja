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

/**
 * Kinetic Battery Model with two charge reservoirs.
 *
 * <p>Charge is in Ah, current is positive when discharging, and time is in
 * hours. The available reservoir supplies the load; the bound reservoir
 * replenishes it at a rate proportional to the difference in their
 * concentrations. The two reservoirs are integrated with the forward-Euler
 * step in {@link #update}, so callers should advance the model at a small,
 * approximately constant interval.
 */
public final class KiBaMBattery implements BatteryModel {
  private final double capacityAh;
  private final double k;
  private final double c;
  private final double vMax;
  private final double vCutoff;
  private double availableAh;
  private double boundAh;
  private boolean dead;

  /**
   * Creates a battery at the given state of charge.
   *
   * @param capacityAh total capacity in ampere-hours
   * @param initialSoc initial state of charge, in [0, 1]
   * @param k inter-reservoir transfer rate, in inverse hours
   * @param c fraction of capacity in the available reservoir, in (0, 1)
   * @param vMax terminal voltage at full available charge, in volts
   * @param vCutoff terminal voltage at zero available charge, in volts
   */
  public KiBaMBattery(double capacityAh, double initialSoc, double k, double c,
                      double vMax, double vCutoff) {
    if (capacityAh <= 0 || initialSoc < 0 || initialSoc > 1 || k <= 0
        || c <= 0 || c >= 1 || vMax <= vCutoff) {
      throw new IllegalArgumentException("invalid KiBaM parameters");
    }
    this.capacityAh = capacityAh;
    this.k = k;
    this.c = c;
    this.vMax = vMax;
    this.vCutoff = vCutoff;
    availableAh = c * initialSoc * capacityAh;
    boundAh = (1 - c) * initialSoc * capacityAh;
  }

  @Override
  public void update(double currentA, double hours) {
    if (dead || hours <= 0) {
      return;
    }
    double transfer = k * (boundAh / (1 - c) - availableAh / c);
    availableAh = Math.max(0, availableAh + (transfer - currentA) * hours);
    boundAh = Math.max(0, boundAh - transfer * hours);
    if (availableAh == 0 || voltage() <= vCutoff) {
      availableAh = 0;
      boundAh = 0;
      dead = true;
    }
  }

  @Override
  public double voltage() {
    double available = Math.max(0, Math.min(1, availableAh / (c * capacityAh)));
    return vCutoff + (vMax - vCutoff) * available;
  }

  @Override
  public double stateOfCharge() {
    return Math.max(0, Math.min(1, (availableAh + boundAh) / capacityAh));
  }

  @Override
  public boolean isDead() {
    return dead;
  }
}
