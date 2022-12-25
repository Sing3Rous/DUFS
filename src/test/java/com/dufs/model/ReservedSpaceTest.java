package com.dufs.model;

import com.dufs.utility.DateUtility;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ReservedSpaceTest {

    @Test
    void serialize() {
        final int clusterSize = 4096;
        final int volumeSize = 4096000;
        char[] volumeNameTmp = Arrays.copyOf("vol.dufs".toCharArray(), 8);
        String volumeName = new String(volumeNameTmp);
        ReservedSpace reservedSpace = new ReservedSpace(volumeNameTmp, clusterSize, volumeSize);
        byte[] bytes = reservedSpace.serialize();
        assertEquals(60, bytes.length);
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        assertEquals(0x44554653, bb.getInt());
        byte[] name = Arrays.copyOfRange(bytes, 4, 20);
        String actualName = new String(name, StandardCharsets.UTF_16);
        assertEquals(volumeName, actualName);
        bb = ByteBuffer.wrap(bytes, 20, 40);
        assertEquals(clusterSize, bb.getInt());
        assertEquals(4197060, bb.getLong());
        int reservedClusters = ((int) Math.ceil((1.0 * volumeSize) / clusterSize));
        assertEquals(reservedClusters, bb.getInt());
        short date = DateUtility.dateToShort(LocalDate.now());
        assertEquals(date, bb.getShort());
        short time = DateUtility.timeToShort(LocalDateTime.now());
        assertEquals(time, bb.getShort());
        assertEquals(date, bb.getShort());
        assertEquals(time, bb.getShort());
        assertEquals(1, bb.getInt());
        assertEquals(reservedClusters - 1, bb.getInt());
        assertEquals(1, bb.getInt());
        assertEquals(0x4A455442, bb.getInt());
    }
}