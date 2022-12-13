package com.dufs.model;

import java.nio.ByteBuffer;

public class ClusterIndexElement {
    private int nextClusterIndex;
    private int prevClusterIndex;

    public byte[] serialize() {
        final int bytesCount = 8;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesCount);
        buffer.putInt(nextClusterIndex);
        buffer.putInt(prevClusterIndex);
        buffer.position(0);
        byte[] tmp = new byte[bytesCount];
        buffer.get(tmp, 0, bytesCount);
        return tmp;
    }
}
