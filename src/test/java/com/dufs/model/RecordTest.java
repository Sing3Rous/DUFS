package com.dufs.model;

import com.dufs.offsets.RecordListOffsets;
import com.dufs.utility.DateUtility;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RecordTest {

    @Test
    void serialize() {
        char[] recordNameTmp = Arrays.copyOf("file".toCharArray(), 32);
        String recordName = new String(recordNameTmp);
        Record record = new Record(recordNameTmp, 1, 65537, 0, (byte) 1);
        byte[] bytes = record.serialize();
        assertEquals(RecordListOffsets.RECORD_SIZE, bytes.length);
        byte[] name = Arrays.copyOfRange(bytes, 0, 64);
        String actualName = new String(name, StandardCharsets.UTF_16);
        assertEquals(recordName, actualName);
        ByteBuffer bb = ByteBuffer.wrap(bytes, 64, 29);
        short date = DateUtility.dateToShort(LocalDate.now());
        assertEquals(date, bb.getShort());
        short time = DateUtility.timeToShort(LocalDateTime.now());
        assertEquals(time, bb.getShort());
        assertEquals(date, bb.getShort());
        assertEquals(time, bb.getShort());
        assertEquals(1, bb.getInt());
        assertEquals(0, bb.getLong());
        assertEquals(65537, bb.getInt());
        assertEquals(0, bb.getInt());
        assertEquals(1, bb.get());
    }
}