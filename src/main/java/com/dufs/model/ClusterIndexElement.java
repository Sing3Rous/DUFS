package com.dufs.model;

import com.dufs.offsets.ClusterIndexListOffsets;

import java.nio.ByteBuffer;

public class ClusterIndexElement {
    private int nextClusterIndex;
    private int prevClusterIndex;
    private final int recordIndex = 0xFFFFFFFF;

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
        final int bytesCount = ClusterIndexListOffsets.CLUSTER_INDEX_ELEMENT_SIZE;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesCount);
        buffer.putInt(nextClusterIndex);
        buffer.putInt(prevClusterIndex);
        buffer.putInt(recordIndex);
        buffer.position(0);
        byte[] tmp = new byte[bytesCount];
        buffer.get(tmp, 0, bytesCount);
        return tmp;
    }
}