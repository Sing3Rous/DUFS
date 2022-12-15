package com.dufs.model;

import com.dufs.utility.VolumeUtility;

import java.nio.ByteBuffer;

public class ClusterIndexList {
    private ClusterIndexElement[] clusterIndexElements;

    public ClusterIndexList(int clusterSize, long volumeSize) {
        clusterIndexElements = new ClusterIndexElement[VolumeUtility.clustersAmount(clusterSize, volumeSize)];
    }
    public byte[] serialize() {
        final int bytesCount = 8 * clusterIndexElements.length;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesCount);
        for (ClusterIndexElement clusterIndexElement : clusterIndexElements) {
            buffer.put(clusterIndexElement.serialize());
        }
        buffer.position(0);
        byte[] tmp = new byte[bytesCount];
        buffer.get(tmp, 0, bytesCount);
        return tmp;
    }
}