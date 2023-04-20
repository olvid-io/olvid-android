/*
 *  Olvid for Android
 *  Copyright © 2019-2023 Olvid SAS
 *
 *  This file is part of Olvid for Android.
 *
 *  Olvid is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License, version 3,
 *  as published by the Free Software Foundation.
 *
 *  Olvid is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with Olvid.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.olvid.engine.datatypes;

import java.util.ArrayList;
import java.util.List;

public class EtaEstimator {
    public static final int MIN_SAMPLE_COUNT = 10;
    public static final long MIN_SAMPLE_DURATION = 10_000L;

    private final long totalBytes;
    private final List<Sample> samples;
    private int offset = 0;

    public EtaEstimator(long initialBytes, long totalBytes) {
        this.totalBytes = totalBytes;
        this.samples = new ArrayList<>();
        this.samples.add(new Sample(System.currentTimeMillis(), initialBytes));
    }

    public void update(long currentBytes) {
        long timestamp = System.currentTimeMillis();
        this.samples.add(new Sample(timestamp, currentBytes));
        // only ever consider the last 10 seconds of sample, but never less than 10 samples
        while (this.samples.size() - offset > MIN_SAMPLE_COUNT
                && timestamp - this.samples.get(offset).timestamp > MIN_SAMPLE_DURATION) {
            offset++;
        }
    }

    public SpeedAndEta getSpeedAndEta() {
        Sample start = samples.get(offset);
        Sample end = samples.get(samples.size() - 1);
        long elapsed = end.timestamp - start.timestamp;
        long xferred = end.byteCount - start.byteCount;
        if (elapsed == 0 || xferred == 0) {
            return new SpeedAndEta(0, 0);
        }
        float speed =  1000 * (float) xferred / (float) elapsed;
        int eta = Math.round((totalBytes - end.byteCount) / speed);
        return new SpeedAndEta(speed, eta);
    }

    private static class Sample {
        long timestamp;
        long byteCount;

        public Sample(long timestamp, long byteCount) {
            this.timestamp = timestamp;
            this.byteCount = byteCount;
        }
    }

    public static class SpeedAndEta {
        public final float speedBps;
        public final int etaSeconds;

        public SpeedAndEta(float speedBps, int etaSeconds) {
            this.speedBps = speedBps;
            this.etaSeconds = etaSeconds;
        }
    }
}
