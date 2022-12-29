package com.dufs.model;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class ClusterIndexElementTest {

    @Test
    void serialize() {
        final int clusterSize = 4096;
        final int volumeSize = 4096000;
        ClusterIndexElement clusterIndexElement = new ClusterIndexElement();
        byte[] bytes = clusterIndexElement.serialize();
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        assertEquals(0, bb.getInt());
        assertEquals(0, bb.getInt());
    }
}