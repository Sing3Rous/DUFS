package com.dufs.model;

import com.dufs.utility.VolumeHelperUtility;

import java.nio.ByteBuffer;

public class ClusterIndexList {
    private final ClusterIndexElement[] clusterIndexElements;

    public ClusterIndexList(int clusterSize, long volumeSize) {
        clusterIndexElements = new ClusterIndexElement[VolumeHelperUtility.clustersAmount(clusterSize, volumeSize)];
        for (int i = 0; i < clusterIndexElements.length; ++i) {
            clusterIndexElements[i] = new ClusterIndexElement();
        }
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