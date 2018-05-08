package org.opennms.oce.model.shell;

import static org.junit.Assert.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ScoreTest {

    @Test
    public void testSameAccuracy100() {
        Path baseline = Paths.get("src", "test", "resources", "Baseline.xml");
        Score score = new Score(baseline, baseline);
        // Test for Incidents
        assertEquals(100, score.getAccuracy());
        assertEquals(0, score.getTypeOneErrorCount());
        assertEquals(0, score.getFalseNegativeCount());
        // Test for Alarms coverage
        assertEquals(100, score.getAlarmAccuracy());
    }

    @Test
    public void testSeventyPercentAccuracy() {
        Path baseline = Paths.get("src", "test", "resources", "Baseline.xml");
        Path seventyPercent = Paths.get("src", "test", "resources", "TwentyPercent.xml");
        Score score = new Score(baseline, seventyPercent);
        // Test for Incidents
        assertEquals(20, score.getAccuracy());
        assertEquals(1, score.getTypeOneErrorCount());
        assertEquals(79, score.getFalseNegativeCount());
        // Test for Alarms coverage
        assertEquals(20, score.getAlarmAccuracy());
    }

}
