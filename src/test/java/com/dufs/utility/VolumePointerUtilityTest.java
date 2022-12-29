package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;
import com.dufs.model.ReservedSpace;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class VolumePointerUtilityTest {

    private static ReservedSpace reservedSpace;

    @BeforeAll
    static void init() {
        reservedSpace = new ReservedSpace(Arrays.copyOf("vol.DUFS".toCharArray(), 8), 4096, 4096000);
    }

    @Test
    void calculateRecordPosition() {
        int recordIndex = 777;
        long recordPosition = VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex);
        assertEquals(80321, recordPosition);
    }

    @Test
    void calculateClusterIndexPosition() {
        int clusterIndex = 777;
        long clusterIndexPosition = VolumePointerUtility.calculateClusterIndexPosition(clusterIndex);
        assertEquals(6276, clusterIndexPosition);

    }

    @Test
    void calculateClusterPosition() {
        int clusterIndex = 777;
        long clusterPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex);
        assertEquals(3283652, clusterPosition);
    }

    @Test
    void calculateRecordListOffset() {
        long recordListOffset = VolumePointerUtility.calculateRecordListOffset(reservedSpace);
        assertEquals(8060, recordListOffset);
    }

    @Test
    void calculateClustersAreaOffset() {
        long clusterAreaOffset = VolumePointerUtility.calculateClustersAreaOffset(reservedSpace);
        assertEquals(101060, clusterAreaOffset);
    }
}