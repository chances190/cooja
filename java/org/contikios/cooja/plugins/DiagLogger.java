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

package org.contikios.cooja.plugins;

import com.github.cliftonlabs.json_simple.Jsoner;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JInternalFrame;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Plugin;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.interfaces.Battery;
import org.contikios.cooja.interfaces.DiagState;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.util.EventTriggers;
import org.jdom2.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Samples every mote on the simulation clock and writes one JSON object per
 * mote and sample, plus death events.
 *
 * <p>State comes from the mote interfaces: {@link DiagState} for routing and
 * MAC counters, {@link Battery} for state of charge, and {@link Radio} events
 * for duty-cycle intervals. Sampling all motes at one timestamp keeps the
 * topology coherent without firmware clocks or log parsing.
 */
@ClassDescription("Diagnostic JSONL logger")
@PluginType(PluginType.PType.SIM_PLUGIN)
public class DiagLogger implements Plugin {
  private static final Logger logger = LoggerFactory.getLogger(DiagLogger.class);

  private final Simulation simulation;
  private final Map<Mote, RadioTracker> radios = new HashMap<>();
  private final Map<Mote, Battery> batteries = new HashMap<>();
  private File output = new File("diag.jsonl");
  private long intervalUs = Simulation.MILLISECOND;
  private PrintWriter writer;
  private TimeEvent sampler;

  public DiagLogger(Simulation simulation, Cooja gui) {
    this.simulation = simulation;
  }

  @Override
  public JInternalFrame getCooja() {
    return null;
  }

  @Override
  public void startPlugin() {
    try {
      writer = new PrintWriter(output, "UTF-8");
    } catch (IOException e) {
      logger.error("Cannot open {}", output, e);
      return;
    }
    simulation.getMoteTriggers().addTrigger(this, (event, mote) -> {
      if (event == EventTriggers.AddRemove.ADD) {
        attach(mote);
      } else {
        detach(mote);
      }
    });
    for (Mote mote : simulation.getMotes()) {
      attach(mote);
    }
    sampler = new TimeEvent() {
      @Override
      public void execute(long t) {
        sample(t);
        simulation.scheduleEvent(this, t + intervalUs);
      }
    };
    simulation.scheduleEvent(sampler, simulation.getSimulationTime() + intervalUs);
  }

  @Override
  public void closePlugin() {
    if (sampler != null) {
      sampler.remove();
      sampler = null;
    }
    simulation.getMoteTriggers().deleteTriggers(this);
    for (Mote mote : simulation.getMotes()) {
      detach(mote);
    }
    if (writer != null) {
      writer.close();
      writer = null;
    }
  }

  private void attach(Mote mote) {
    Radio radio = mote.getInterfaces().getRadio();
    if (radio != null) {
      RadioTracker tracker = new RadioTracker(radio);
      radios.put(mote, tracker);
      radio.getRadioEventTriggers().addTrigger(this,
          (event, source) -> tracker.flush(simulation.getSimulationTime()));
    }
    Battery battery = mote.getInterfaces().getBattery();
    if (battery != null) {
      batteries.put(mote, battery);
      battery.getBatteryEvents().addTrigger(this, (event, source) -> {
        if (event == Battery.BatteryEvent.DEAD) {
          Map<String, Object> death = new LinkedHashMap<>();
          death.put("kind", "death");
          death.put("time_us", simulation.getSimulationTime());
          death.put("mote", mote.getID());
          write(death);
          flush();
        }
      });
    }
  }

  private void detach(Mote mote) {
    Radio radio = mote.getInterfaces().getRadio();
    if (radio != null) {
      radio.getRadioEventTriggers().deleteTriggers(this);
    }
    Battery battery = batteries.remove(mote);
    if (battery != null) {
      battery.getBatteryEvents().deleteTriggers(this);
    }
    radios.remove(mote);
  }

  private void sample(long t) {
    for (Mote mote : simulation.getMotes()) {
      DiagState diag = mote.getInterfaces().getInterfaceOfType(DiagState.class);
      if (diag == null) {
        continue;
      }
      Map<String, Object> state = diag.getState();
      if (state == null) {
        continue;
      }
      RadioTracker radio = radios.get(mote);
      if (radio != null) {
        radio.flush(t);
      }
      write(record(t, mote, state, batteries.get(mote), radio));
    }
    flush();
  }

  private static Map<String, Object> record(long t, Mote mote,
                                            Map<String, Object> diag,
                                            Battery battery, RadioTracker radio) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put("soc", battery != null ? battery.getStateOfCharge() : 0);
    state.put("voltage", battery != null ? battery.getVoltage() : 0);
    state.put("dead", battery != null && battery.isDead());

    Map<String, Object> duty = new LinkedHashMap<>();
    duty.put("on_us", radio != null ? radio.onUs : 0);
    duty.put("tx_us", radio != null ? radio.txUs : 0);
    duty.put("rx_us", radio != null ? radio.rxUs : 0);
    duty.put("idle_us", radio != null ? radio.idleUs : 0);
    duty.put("interfered_us", radio != null ? radio.interferedUs : 0);
    duty.put("off_us", radio != null ? radio.offUs : 0);

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("kind", "sample");
    record.put("time_us", t);
    record.put("mote", mote.getID());
    record.put("battery", state);
    record.put("radio", duty);
    record.put("diag", diag);
    return record;
  }

  private void write(Map<String, Object> record) {
    if (writer != null) {
      writer.println(Jsoner.serialize(record));
    }
  }

  private void flush() {
    if (writer != null) {
      writer.flush();
    }
  }

  @Override
  public Collection<Element> getConfigXML() {
    List<Element> config = new ArrayList<>();
    Element element = new Element("output");
    element.setText(output.getPath());
    config.add(element);
    element = new Element("interval_ms");
    element.setText(Long.toString(intervalUs / Simulation.MILLISECOND));
    config.add(element);
    return config;
  }

  @Override
  public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
    for (Element element : configXML) {
      if ("output".equals(element.getName())) {
        output = new File(element.getText());
      } else if ("interval_ms".equals(element.getName())) {
        intervalUs = Math.max(Simulation.MILLISECOND,
            Long.parseLong(element.getText()) * Simulation.MILLISECOND);
      }
    }
    return true;
  }

  /**
   * Accumulates the interval that just ended in the state radio was in, then
   * latches the state radio is in now.
   */
  private static final class RadioTracker {
    private enum State {
      OFF, IDLE, TX, RX, INTERFERED
    }

    private final Radio radio;
    private State state;
    private long lastUs;
    private long onUs;
    private long txUs;
    private long rxUs;
    private long idleUs;
    private long interferedUs;
    private long offUs;

    RadioTracker(Radio radio) {
      this.radio = radio;
      this.lastUs = radio.getMote().getSimulation().getSimulationTime();
      this.state = stateOf(radio);
    }

    void flush(long t) {
      long delta = t - lastUs;
      if (delta > 0) {
        switch (state) {
          case OFF -> offUs += delta;
          case IDLE -> {
            idleUs += delta;
            onUs += delta;
          }
          case TX -> {
            txUs += delta;
            onUs += delta;
          }
          case RX -> {
            rxUs += delta;
            onUs += delta;
          }
          case INTERFERED -> {
            interferedUs += delta;
            onUs += delta;
          }
        }
        lastUs = t;
      }
      state = stateOf(radio);
    }

    private static State stateOf(Radio radio) {
      if (!radio.isRadioOn()) {
        return State.OFF;
      }
      if (radio.isTransmitting()) {
        return State.TX;
      }
      if (radio.isInterfered()) {
        return State.INTERFERED;
      }
      if (radio.isReceiving()) {
        return State.RX;
      }
      return State.IDLE;
    }
  }
}
