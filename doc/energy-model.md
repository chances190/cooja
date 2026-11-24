# MSP mote energy model

The battery model sums the MCU baseline current and the modeled radio current. Values are typical at approximately 3 V unless the source gives only a range; in that case the implementation uses the published upper endpoint. This estimates the target electronics, not power drawn by an attached debugger or USB host.

| Platform/component | Active / RX / TX / idle | Low-power | Source |
|---|---:|---:|---|
| Z1 board baseline | 10 mA | 23 µA | [Contiki-NG energy tutorial](https://docs.contiki-ng.org/en/develop/doc/tutorials/Instrumenting-Contiki-NG-applications-with-energy-usage-estimation.html), citing the [Z1 Rev. C datasheet](https://github.com/Zolertia/Resources/blob/master/Z1/Hardware/Revision%20C/Datasheets/Zolertia%20Z1%20datasheet%20Revision%20C.pdf) |
| Tmote Sky board baseline | 2.4 mA | 21 µA | [Tmote Sky datasheet](https://www.snm.ethz.ch/snmwiki/pub/uploads/Projects/tmote_sky_datasheet.pdf), MCU on/radio off and MCU standby maximum values |
| MSP430F5438 MCU | 312 µA/MHz, flash execution | 2.6 µA | [TI datasheet](https://www.ti.com/lit/ds/symlink/msp430f5438.pdf), 3 V typical |
| MSP430FR5969 MCU | 100 µA/MHz | 0.4 µA | [TI datasheet](https://www.ti.com/lit/ds/symlink/msp430fr5969.pdf), 3 V typical |
| CC2420 radio | 18.8 mA RX; 8.5–17.4 mA TX by output setting; 426 µA idle | 0.02 µA off | [TI datasheet](https://www.ti.com/lit/ds/symlink/cc2420.pdf); the Sky board's RX and idle values use its integrated-radio figures of 19.7 mA and 365 µA |
| CC2520 radio | 18.5 mA receiving; 22.3 mA waiting for a frame; 25.8 mA TX at 0 dBm; 33.6 mA TX at +5 dBm; 1.9 mA active idle | <1 µA power-down | [TI datasheet](https://www.ti.com/lit/ds/symlink/cc2520.pdf), 3 V typical |

The MSP430F5438 and FR5969 values are MCU-only estimates. The LaunchPad peripherals, BoosterPacks, external sensors, and debugger are not included. CC2520 transmission current is supported only at the two datasheet points above. CC1101 and CC1120 radio currents are not modeled: the current depends on frequency and programmed power, which the current MSPSim wrappers do not expose reliably. A configured battery on unsupported radio settings is disabled instead of producing invented results.

The kinetic battery model advances its two charge reservoirs with forward-Euler steps. The default interval is 10 ms and is configurable per battery; a small interval is required because the integration is explicit.

Power-off cancels all mote-scoped simulation events, stops future firmware execution and invokes the radio wrapper's hardware shutdown. The mote remains registered so its death log and visualizers remain observable.
