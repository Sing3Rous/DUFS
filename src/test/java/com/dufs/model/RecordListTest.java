package com.dufs.model;

import com.dufs.offsets.RecordListOffsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordListTest {

    @Test
    void serialize() {
        final int clusterSize = 4096;
        final int volumeSize = 4096000;
        RecordList recordList = new RecordList(clusterSize, volumeSize);
        byte[] bytes = recordList.serialize();
        int recordsNumber = ((int) Math.ceil((1.0 * volumeSize) / clusterSize));
        assertEquals(recordsNumber * RecordListOffsets.RECORD_SIZE, bytes.length);
    }
}