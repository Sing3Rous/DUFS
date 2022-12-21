package com.dufs.model;

import java.nio.ByteBuffer;

public class ClusterIndexElement {
    private int nextClusterIndex;
    private int prevClusterIndex;

    public int getNextClusterIndex() {
        return nextClusterIndex;
    }

    public void setNextClusterIndex(int nextClusterIndex) {
        this.nextClusterIndex = nextClusterIndex;
    }

    public int getPrevClusterIndex() {
        return prevClusterIndex;
    }

    public void setPrevClusterIndex(int prevClusterIndex) {
        this.prevClusterIndex = prevClusterIndex;
    }

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