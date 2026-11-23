package com.example.gajeontiming.b2b;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MonitorFormatTest {

    @Test
    public void 변동률은_화살표와_소수_한_자리() {
        assertEquals("▼3.2%", MonitorFormat.pct(-3.2));
        assertEquals("▲2.1%", MonitorFormat.pct(2.1));
        assertEquals("▲12.0%", MonitorFormat.pct(12.0));
        assertEquals("▼0.5%", MonitorFormat.pct(-0.46));   // 반올림
    }

    @Test
    public void 영이나_모르면_화살표_없음() {
        assertEquals("0.0%", MonitorFormat.pct(0.0));
        assertEquals("0.0%", MonitorFormat.pct(-0.04));   // 반올림하면 0
        assertEquals("-", MonitorFormat.pct(null));
    }

    @Test
    public void 서버_흐름별_색() {
        assertEquals(MonitorFormat.Tone.DOWN, MonitorFormat.tone("down"));
        assertEquals(MonitorFormat.Tone.UP, MonitorFormat.tone("up"));
        assertEquals(MonitorFormat.Tone.NEUTRAL, MonitorFormat.tone("neutral"));
        assertEquals(MonitorFormat.Tone.NEUTRAL, MonitorFormat.tone("realtime"));
        assertEquals(MonitorFormat.Tone.NEUTRAL, MonitorFormat.tone("buy"));   // 예전 값은 회색
        assertEquals(MonitorFormat.Tone.NEUTRAL, MonitorFormat.tone(null));
    }

    @Test
    public void 변동률_색은_내리면_파랑_오르면_빨강() {
        assertEquals(MonitorFormat.Tone.DOWN, MonitorFormat.changeTone(-1.0));
        assertEquals(MonitorFormat.Tone.UP, MonitorFormat.changeTone(1.0));
        assertEquals(MonitorFormat.Tone.NEUTRAL, MonitorFormat.changeTone(0.0));
        assertEquals(MonitorFormat.Tone.NEUTRAL, MonitorFormat.changeTone(null));
    }
}
