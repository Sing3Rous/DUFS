package com.dufs.model;

import com.dufs.offsets.ClusterIndexListOffsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClusterIndexListTest {

    @Test
    void serialize() {
        final int clusterSize = 4096;
        final int volumeSize = 4096000;
        ClusterIndexList clusterIndexList = new ClusterIndexList(clusterSize, volumeSize);
        byte[] bytes = clusterIndexList.serialize();
        int clustersNumber = ((int) Math.ceil((1.0 * volumeSize) / clusterSize));
        assertEquals(clustersNumber * ClusterIndexListOffsets.CLUSTER_INDEX_ELEMENT_SIZE, bytes.length);
    }
}