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

package org.contikios.cooja.mspmote.interfaces;

import java.awt.GridLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.DiagState;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.mote.memory.MemoryInterface.Symbol;

/**
 * Decodes the experiment wrapper around {@code struct rpl_snapshot}.
 *
 * <p>The wrapper offsets mirror {@code firmware/diag.h}; the embedded RPL part
 * mirrors {@code os/net/routing/rpl-lite/rpl-snapshot.h}. Two identical
 * consecutive reads are required so a snapshot being rewritten is never
 * decoded.
 *
 * <p>Experiment-local: the wrapper is owned by {@code firmware/diag.h}, not by
 * Contiki-NG, so this class is not part of the Cooja upstream API.
 */
@ClassDescription("RPL diag state")
public class MspDiagState implements DiagState {
  private static final String SYMBOL = "diag_snapshot";
  private static final int MAGIC = 0xD1A6;
  private static final int VERSION = 3;

  private static final int OFF_MAGIC = 0;
  private static final int OFF_VERSION = 2;
  private static final int OFF_NODE = 6;
  private static final int OFF_PTX = 8;
  private static final int OFF_CTRL = 10;
  private static final int OFF_MAC = 30;
  private static final int OFF_RPL = 46;

  private static final int OFF_RPL_USED = 0;
  private static final int OFF_RPL_STATE = 1;
  private static final int OFF_RPL_RANK = 2;
  private static final int OFF_RPL_PARENT = 4;
  private static final int OFF_RPL_PARENT_RANK = 12;
  private static final int OFF_RPL_PARENT_ETX = 14;
  private static final int OFF_RPL_PARENT_RSSI = 16;
  private static final int OFF_RPL_NBR_COUNT = 18;
  private static final int OFF_RPL_NEIGHBORS = 20;

  private static final int RPL_NEIGHBOR_SIZE = 24;
  private static final int MIN_SNAPSHOT_SIZE = OFF_RPL + OFF_RPL_NEIGHBORS;
  private static final int READ_ATTEMPTS = 3;

  private final Mote mote;
  private MemoryInterface memory;
  private Symbol symbol;

  public MspDiagState(Mote mote) {
    this.mote = mote;
  }

  @Override
  public Map<String, Object> getState() {
    Snapshot snapshot = readSnapshot();
    return snapshot == null ? null : toMap(snapshot);
  }

  private Snapshot readSnapshot() {
    MemoryInterface current = mote.getMemory();
    if (current == null) {
      return null;
    }
    if (current != memory || symbol == null) {
      memory = current;
      symbol = current.getSymbolMap().get(SYMBOL);
    }
    if (symbol == null || symbol.size < MIN_SNAPSHOT_SIZE
        || (symbol.size - MIN_SNAPSHOT_SIZE) % RPL_NEIGHBOR_SIZE != 0) {
      return null;
    }
    int maxNeighbors = (symbol.size - MIN_SNAPSHOT_SIZE) / RPL_NEIGHBOR_SIZE;
    for (int attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
      byte[] data = memory.getMemorySegment(symbol.addr, symbol.size);
      if (!valid(data, maxNeighbors)) {
        continue;
      }
      byte[] check = memory.getMemorySegment(symbol.addr, symbol.size);
      if (Arrays.equals(data, check)) {
        return decode(data, maxNeighbors);
      }
    }
    return null;
  }

  private static boolean valid(byte[] data, int maxNeighbors) {
    return u16(data, OFF_MAGIC) == MAGIC
        && (data[OFF_VERSION] & 0xff) == VERSION
        && (data[OFF_RPL + OFF_RPL_NBR_COUNT] & 0xff) <= maxNeighbors;
  }

  private static Snapshot decode(byte[] data, int maxNeighbors) {
    ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    Counters counters = new Counters(
        u16(data, OFF_CTRL), u16(data, OFF_CTRL + 2),
        u16(data, OFF_CTRL + 4), u16(data, OFF_CTRL + 6),
        u16(data, OFF_CTRL + 8), u16(data, OFF_CTRL + 10),
        u16(data, OFF_CTRL + 12), u16(data, OFF_CTRL + 14),
        u16(data, OFF_CTRL + 16), u16(data, OFF_CTRL + 18));
    Mac mac = new Mac(
        u16(data, OFF_MAC), u16(data, OFF_MAC + 2),
        u16(data, OFF_MAC + 4), u16(data, OFF_MAC + 6),
        u16(data, OFF_MAC + 8), u16(data, OFF_MAC + 10),
        u16(data, OFF_MAC + 12), u16(data, OFF_MAC + 14));

    int count = Math.min(data[OFF_RPL + OFF_RPL_NBR_COUNT] & 0xff, maxNeighbors);
    List<Neighbor> neighbors = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      int off = OFF_RPL + OFF_RPL_NEIGHBORS + i * RPL_NEIGHBOR_SIZE;
      neighbors.add(new Neighbor(
          addrId(data, off), u16(data, off + 8), u16(data, off + 10),
          b.getShort(off + 12), data[off + 14] & 0xff, data[off + 15] != 0,
          u16(data, off + 16), u16(data, off + 18),
          u16(data, off + 20), u16(data, off + 22)));
    }

    int used = data[OFF_RPL + OFF_RPL_USED] & 0xff;
    int state = used != 0 ? data[OFF_RPL + OFF_RPL_STATE] & 0xff : 0xff;
    return new Snapshot(
        u16(data, OFF_NODE), data[OFF_PTX], DagState.of(state),
        u16(data, OFF_RPL + OFF_RPL_RANK), addrId(data, OFF_RPL + OFF_RPL_PARENT),
        u16(data, OFF_RPL + OFF_RPL_PARENT_RANK),
        u16(data, OFF_RPL + OFF_RPL_PARENT_ETX),
        b.getShort(OFF_RPL + OFF_RPL_PARENT_RSSI), counters, mac,
        List.copyOf(neighbors));
  }

  /* The Z1 link-layer address is 8 bytes; the node id uses its last two. */
  private static int addrId(byte[] data, int offset) {
    return (data[offset + 7] & 0xff) | ((data[offset + 6] & 0xff) << 8);
  }

  private static Map<String, Object> toMap(Snapshot snapshot) {
    Map<String, Object> rpl = new LinkedHashMap<>();
    rpl.put("node", snapshot.node());
    rpl.put("tx_power_dbm", snapshot.txPowerDbm());
    rpl.put("state", snapshot.state().name().toLowerCase());
    rpl.put("rank", snapshot.rank());
    rpl.put("parent", snapshot.parent());
    rpl.put("parent_rank", snapshot.parentRank());
    rpl.put("parent_etx", snapshot.parentEtx());
    rpl.put("parent_rssi", snapshot.parentRssi());
    rpl.put("nbr_count", snapshot.neighbors().size());

    Counters counters = snapshot.counters();
    Map<String, Object> ctrl = new LinkedHashMap<>();
    ctrl.put("dio_tx", counters.dioTx());
    ctrl.put("dio_rx", counters.dioRx());
    ctrl.put("dis_tx", counters.disTx());
    ctrl.put("dis_rx", counters.disRx());
    ctrl.put("dao_tx", counters.daoTx());
    ctrl.put("dao_rx", counters.daoRx());
    ctrl.put("dao_ack_tx", counters.daoAckTx());
    ctrl.put("dao_ack_rx", counters.daoAckRx());
    ctrl.put("udp_tx", counters.udpTx());
    ctrl.put("udp_rx", counters.udpRx());

    Mac mac = snapshot.mac();
    Map<String, Object> macCounters = new LinkedHashMap<>();
    macCounters.put("tx_data", mac.txData());
    macCounters.put("rx_data", mac.rxData());
    macCounters.put("tx_uc", mac.txUc());
    macCounters.put("rx_uc", mac.rxUc());
    macCounters.put("tx_bc", mac.txBc());
    macCounters.put("rx_bc", mac.rxBc());
    macCounters.put("tx_ack", mac.txAck());
    macCounters.put("rx_ack", mac.rxAck());

    List<Map<String, Object>> neighbors = new ArrayList<>();
    for (Neighbor neighbor : snapshot.neighbors()) {
      Map<String, Object> n = new LinkedHashMap<>();
      n.put("address", neighbor.address());
      n.put("rank", neighbor.rank());
      n.put("etx", neighbor.etx());
      n.put("rssi", neighbor.rssi());
      n.put("freshness", neighbor.freshness());
      n.put("parent", neighbor.parent());
      n.put("tx", neighbor.tx());
      n.put("acked", neighbor.acked());
      n.put("rx", neighbor.rx());
      n.put("drops", neighbor.drops());
      neighbors.add(n);
    }

    Map<String, Object> state = new LinkedHashMap<>();
    state.put("rpl", rpl);
    state.put("ctrl", ctrl);
    state.put("mac", macCounters);
    state.put("neighbors", neighbors);
    return state;
  }

  private static int u16(byte[] data, int offset) {
    return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
  }

  @Override
  public JPanel getInterfaceVisualizer() {
    var panel = new JPanel(new GridLayout(0, 1));
    Snapshot snapshot = readSnapshot();
    if (snapshot == null) {
      panel.add(new JLabel("Diagnostic snapshot unavailable"));
      return panel;
    }
    panel.add(new JLabel(String.format("Node %d  %s  rank %d",
        snapshot.node(), snapshot.state().name().toLowerCase(), snapshot.rank())));
    panel.add(new JLabel(String.format("Parent %d  rank %d  ETX %d  RSSI %d dBm",
        snapshot.parent(), snapshot.parentRank(), snapshot.parentEtx(),
        snapshot.parentRssi())));
    panel.add(new JLabel(String.format("Neighbors %d  TX power %d dBm",
        snapshot.neighbors().size(), snapshot.txPowerDbm())));
    return panel;
  }

  @Override
  public void releaseInterfaceVisualizer(JPanel panel) {
  }

  private enum DagState {
    INITIALIZED, JOINED, REACHABLE, POISONING, UNUSED;

    static DagState of(int value) {
      return value >= 0 && value <= POISONING.ordinal() ? values()[value] : UNUSED;
    }
  }

  private record Counters(int dioTx, int dioRx, int disTx, int disRx,
                          int daoTx, int daoRx, int daoAckTx, int daoAckRx,
                          int udpTx, int udpRx) {
  }

  private record Mac(int txData, int rxData, int txUc, int rxUc,
                     int txBc, int rxBc, int txAck, int rxAck) {
  }

  private record Neighbor(int address, int rank, int etx, int rssi, int freshness,
                          boolean parent, int tx, int acked, int rx, int drops) {
  }

  private record Snapshot(int node, int txPowerDbm, DagState state, int rank,
                          int parent, int parentRank, int parentEtx, int parentRssi,
                          Counters counters, Mac mac, List<Neighbor> neighbors) {
  }
}
