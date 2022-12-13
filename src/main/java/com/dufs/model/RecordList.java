package com.dufs.model;

import com.dufs.utility.VolumeUtility;

import java.nio.ByteBuffer;

public class RecordList {
    private Record[] records;

    public RecordList(int clusterSize, long volumeSize) {
        records = new Record[VolumeUtility.clustersAmount(clusterSize, volumeSize)];
    }

    public byte[] serialize() {
        final int bytesCount = 89 * records.length;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesCount);
        for (Record record : records) {
            buffer.put(record.serialize());
        }
        buffer.position(0);
        byte[] tmp = new byte[bytesCount];
        buffer.get(tmp, 0, bytesCount);
        return tmp;
    }
}
