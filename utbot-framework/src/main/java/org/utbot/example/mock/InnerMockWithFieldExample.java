package org.utbot.example.mock;

import org.utbot.example.mock.others.VersionStamp;

public class InnerMockWithFieldExample {
    public VersionStamp stamp;

    public static VersionStamp checkAndUpdate(InnerMockWithFieldExample example) {
        if (example.stamp.initial > example.stamp.version) {
            example.stamp.version = example.stamp.initial;
        } else {
            example.stamp.version = example.stamp.version + 1;
        }
        return example.stamp;
    }
}
