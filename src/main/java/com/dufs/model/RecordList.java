package com.dufs.model;

import com.dufs.offsets.RecordListOffsets;
import com.dufs.utility.VolumeHelper;

import java.nio.ByteBuffer;

public class RecordList {
    private final Record[] records;

    public RecordList(int clusterSize, long volumeSize) {
        records = new Record[VolumeHelper.clustersAmount(clusterSize, volumeSize)];
        for (int i = 0; i < records.length; ++i) {
            records[i] = new Record();
        }
    }

    public byte[] serialize() {
        final int bytesCount = RecordListOffsets.RECORD_SIZE * records.length;
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