package org.utbot.example.mock;

import org.utbot.api.mock.UtMock;
import org.utbot.example.mock.others.Network;

public class UseNetwork {
    public static int readBytes(byte[] packet, Network network) {
        int res = 0;
        int c;
        while ((c = network.nextByte()) != -1) {
            packet[res++] = (byte)c;
        }
        return res;
    }

    public void mockVoidMethod(Network network) {
        UtMock.assume(network != null);

        network.voidMethod();
    }
}