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

/**
 * Kinetic Battery Model. Charge is in Ah, current is positive when discharging,
 * and time is in hours.
 */
public final class KiBaMBattery implements BatteryModel {
  private final double capacityAh;
  private final double initialSoc;
  private final double k;
  private final double c;
  private final double vMax;
  private final double vCutoff;
  private double q1;
  private double q2;
  private boolean dead;

  public KiBaMBattery(double capacityAh, double initialSoc, double k, double c,
                      double vMax, double vCutoff) {
    if (capacityAh <= 0 || initialSoc < 0 || initialSoc > 1 || k <= 0
        || c <= 0 || c >= 1 || vMax <= vCutoff) {
      throw new IllegalArgumentException("invalid KiBaM parameters");
    }
    this.capacityAh = capacityAh;
    this.initialSoc = initialSoc;
    this.k = k;
    this.c = c;
    this.vMax = vMax;
    this.vCutoff = vCutoff;
    double charge = initialSoc * capacityAh;
    q1 = c * charge;
    q2 = (1 - c) * charge;
  }

  @Override
  public void update(double currentA, double hours) {
    if (dead || hours <= 0) {
      return;
    }
    double q0 = q1 + q2;
    double r = Math.exp(-k * hours);
    double denominator = 1 - r + c * (k * hours - 1 + r);
    if (denominator == 0) {
      return;
    }
    double maxDischarge = (k * q1 * r + q0 * k * c * (1 - r)) / denominator;
    double maxCharge = (-k * c * capacityAh + k * q1 * r
        + q0 * k * c * (1 - r)) / denominator;
    double current = Math.max(maxCharge, Math.min(currentA, maxDischarge));

    double nextQ1 = q1 * r
        + ((q0 * k * c - current) * (1 - r)
        - current * c * (k * hours - 1 + r)) / k;
    double nextQ2 = q2 * r + q0 * (1 - c) * (1 - r)
        - current * (1 - c) * (k * hours - 1 + r) / k;
    q1 = Math.max(0, nextQ1);
    q2 = Math.max(0, nextQ2);
    if (q1 <= 1e-12 || voltage() <= vCutoff) {
      q1 = 0;
      q2 = 0;
      dead = true;
    }
  }

  @Override
  public double voltage() {
    double available = Math.max(0, Math.min(1, q1 / (c * capacityAh)));
    return vCutoff + (vMax - vCutoff) * available;
  }

  @Override
  public double stateOfCharge() {
    return Math.max(0, Math.min(1, (q1 + q2) / capacityAh));
  }

  @Override
  public boolean isDead() {
    return dead;
  }

  public double capacityAh() {
    return capacityAh;
  }

  public double initialSoc() {
    return initialSoc;
  }

  public double k() {
    return k;
  }

  public double c() {
    return c;
  }

  public double vMax() {
    return vMax;
  }

  public double vCutoff() {
    return vCutoff;
  }
}
