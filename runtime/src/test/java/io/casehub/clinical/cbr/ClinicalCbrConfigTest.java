package io.casehub.clinical.cbr;

import io.casehub.neocortex.memory.cbr.ScopeDecay;
import io.casehub.neocortex.memory.cbr.TemporalDecay;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ClinicalCbrConfigTest {

    @Test
    void parseScopeDecay_exponential() {
        ScopeDecay decay = ClinicalCbrConfig.parseScopeDecay("exponential:0.7");
        assertInstanceOf(ScopeDecay.Exponential.class, decay);
        assertEquals(0.7, ((ScopeDecay.Exponential) decay).base(), 0.001);
    }

    @Test
    void parseScopeDecay_linear() {
        ScopeDecay decay = ClinicalCbrConfig.parseScopeDecay("linear:3");
        assertInstanceOf(ScopeDecay.Linear.class, decay);
        assertEquals(3, ((ScopeDecay.Linear) decay).maxDepth());
    }

    @Test
    void parseScopeDecay_step() {
        ScopeDecay decay = ClinicalCbrConfig.parseScopeDecay("step:0.5");
        assertInstanceOf(ScopeDecay.Step.class, decay);
        assertEquals(0.5, ((ScopeDecay.Step) decay).beyondExact(), 0.001);
    }

    @Test
    void parseScopeDecay_null_returnsNull() {
        assertNull(ClinicalCbrConfig.parseScopeDecay(null));
    }

    @Test
    void parseTemporalDecay_halflife() {
        TemporalDecay decay = ClinicalCbrConfig.parseTemporalDecay("halflife:90d");
        assertInstanceOf(TemporalDecay.HalfLife.class, decay);
        assertEquals(Duration.ofDays(90), ((TemporalDecay.HalfLife) decay).halfLife());
    }

    @Test
    void parseTemporalDecay_linear() {
        TemporalDecay decay = ClinicalCbrConfig.parseTemporalDecay("linear:365d");
        assertInstanceOf(TemporalDecay.Linear.class, decay);
        assertEquals(Duration.ofDays(365), ((TemporalDecay.Linear) decay).zeroAt());
    }

    @Test
    void parseTemporalDecay_step() {
        TemporalDecay decay = ClinicalCbrConfig.parseTemporalDecay("step:30d:0.3");
        assertInstanceOf(TemporalDecay.Step.class, decay);
        assertEquals(Duration.ofDays(30), ((TemporalDecay.Step) decay).cutoff());
        assertEquals(0.3, ((TemporalDecay.Step) decay).afterCutoff(), 0.001);
    }

    @Test
    void parseTemporalDecay_null_returnsNull() {
        assertNull(ClinicalCbrConfig.parseTemporalDecay(null));
    }

    @Test
    void parseScopeDecay_invalidFormat_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> ClinicalCbrConfig.parseScopeDecay("invalid"));
    }

    @Test
    void parseTemporalDecay_invalidFormat_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> ClinicalCbrConfig.parseTemporalDecay("invalid"));
    }
}
