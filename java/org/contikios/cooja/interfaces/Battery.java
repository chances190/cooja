/*
 * Copyright (c) 2008, Swedish Institute of Computer Science.
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
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package org.contikios.cooja.interfaces;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.EnergyState;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteInterface;
import org.contikios.cooja.MoteTimeEvent;
import org.contikios.cooja.PowerOffMote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.battery.BatteryModel;
import org.contikios.cooja.battery.KiBaMBattery;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A battery connected to a mote. The model integrates the mote's current
 * consumption reported through {@link EnergyState}.
 */
@ClassDescription("Battery")
public class Battery implements MoteInterface {
  private static final Logger logger = LoggerFactory.getLogger(Battery.class);

  public enum BatteryEvent {
    LOW, DEAD
  }

  private final Mote mote;
  private final EventTriggers<BatteryEvent, Battery> batteryEvents = new EventTriggers<>();
  private BatteryModel model;
  private EnergyState energyState;
  private double capacityMah;
  private double initialSoc;
  private double k;
  private double c;
  private double vMax;
  private double vCutoff;
  private MoteTimeEvent updateEvent;
  private long intervalUs = 1_000_000;
  private long lastUpdateUs;
  private double currentA;
  private boolean dead;
  private boolean lowReported;
  private double lowSoc = 0.1;

  private String modelError;
  private JLabel stateLabel;

  public Battery(Mote mote) {
    this.mote = mote;
  }

  @Override
  public void added() {
    if (model == null) {
      return;
    }
    if (!(mote instanceof EnergyState state)) {
      disableModel("Mote does not report energy consumption");
      return;
    }
    if (!(mote instanceof PowerOffMote)) {
      disableModel("Mote cannot be powered off");
      return;
    }
    energyState = state;
    modelError = null;
    lastUpdateUs = mote.getSimulation().getSimulationTime();
    try {
      currentA = energyState.getCurrentConsumption();
    } catch (UnsupportedOperationException e) {
      disableModel(e.getMessage());
      return;
    }
    energyState.getEnergyStateTriggers().addTrigger(this, (event, source) -> onLoadChanged());
    updateEvent = new MoteTimeEvent(mote) {
      @Override
      public void execute(long t) {
        advanceTo(t);
        if (!dead) {
          mote.getSimulation().scheduleEvent(this, t + intervalUs);
        }
      }
    };
    mote.getSimulation().scheduleEvent(updateEvent, lastUpdateUs + intervalUs);
  }

  @Override
  public void removed() {
    if (updateEvent != null) {
      updateEvent.remove();
      updateEvent = null;
    }
    if (energyState != null) {
      energyState.getEnergyStateTriggers().deleteTriggers(this);
      energyState = null;
    }
  }

  private void onLoadChanged() {
    long t = mote.getSimulation().getSimulationTime();
    double next;
    try {
      next = energyState.getCurrentConsumption();
    } catch (UnsupportedOperationException e) {
      disableModel(e.getMessage());
      return;
    }
    if (next == currentA) {
      return;
    }
    advanceTo(t);
    currentA = next;
  }

  private void disableModel(String reason) {
    logger.error("Battery model unavailable for mote {}: {}", mote.getID(), reason);
    modelError = reason;
    removed();
    updateVisualizer();
  }

  private void advanceTo(long t) {
    if (dead || t <= lastUpdateUs) {
      return;
    }
    double hours = (t - lastUpdateUs) / 3.6e9;
    lastUpdateUs = t;
    model.update(currentA, hours);
    if (!lowReported && model.stateOfCharge() <= lowSoc) {
      lowReported = true;
      batteryEvents.trigger(BatteryEvent.LOW, this);
    }
    if (model.isDead()) {
      dead = true;
      batteryEvents.trigger(BatteryEvent.DEAD, this);
      logger.info("Mote {} battery depleted at {} ms", mote.getID(),
                  t / Simulation.MILLISECOND);
      mote.getSimulation().invokeSimulationThread(
          ((PowerOffMote) mote)::powerOff);
    }
    updateVisualizer();
  }

  public EventTriggers<BatteryEvent, Battery> getBatteryEvents() {
    return batteryEvents;
  }

  public double getVoltage() {
    return model == null ? 0 : model.voltage();
  }

  public double getStateOfCharge() {
    return model == null ? 0 : model.stateOfCharge();
  }

  public boolean isDead() {
    return dead;
  }

  @Override
  public Collection<Element> getConfigXML() {
    if (model == null) {
      return null;
    }
    var config = new ArrayList<Element>();
    var element = new Element("battery");
    element.setAttribute("capacity_mah", Double.toString(capacityMah));
    element.setAttribute("initial_soc", Double.toString(initialSoc));
    element.setAttribute("k_per_h", Double.toString(k));
    element.setAttribute("c", Double.toString(c));
    element.setAttribute("v_max", Double.toString(vMax));
    element.setAttribute("v_cutoff", Double.toString(vCutoff));
    element.setAttribute("low_soc", Double.toString(lowSoc));
    element.setAttribute("interval_ms",
        Long.toString(intervalUs / Simulation.MILLISECOND));
    config.add(element);
    return config;
  }

  @Override
  public void setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (var element : configXML) {
      if (!"battery".equals(element.getName())) {
        continue;
      }
      capacityMah = attr(element, "capacity_mah", 0);
      initialSoc = attr(element, "initial_soc", 1);
      k = attr(element, "k_per_h", 0.1);
      c = attr(element, "c", 0.5);
      vMax = attr(element, "v_max", 3.0);
      vCutoff = attr(element, "v_cutoff", 2.0);
      lowSoc = attr(element, "low_soc", 0.1);
      intervalUs = Math.max(1,
          (long) (attr(element, "interval_ms", 1000) * Simulation.MILLISECOND));
      if (capacityMah > 0) {
        model = new KiBaMBattery(capacityMah / 1000, initialSoc, k, c,
            vMax, vCutoff);
      }
      return;
    }
  }

  private static double attr(Element element, String name, double fallback) {
    String value = element.getAttributeValue(name);
    return value == null ? fallback : Double.parseDouble(value);
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    var panel = new JPanel(new GridLayout(0, 1));
    stateLabel = new JLabel();
    panel.add(stateLabel);
    updateVisualizer();
    return panel;
  }

  private void updateVisualizer() {
    if (stateLabel != null) {
      stateLabel.setText(modelError == null
          ? String.format("SoC %.3f  V %.3f%s",
              getStateOfCharge(), getVoltage(), dead ? "  DEAD" : "")
          : "Energy model unavailable: " + modelError);
    }
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
    stateLabel = null;
  }
}
