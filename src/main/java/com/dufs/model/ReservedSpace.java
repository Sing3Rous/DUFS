package com.dufs.model;

import com.dufs.utility.DateUtility;
import com.dufs.utility.VolumeHelperUtility;
import com.dufs.utility.VolumeUtility;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReservedSpace {
    private int dufsNoseSignature = 0x44554653; // "DUFS"
    private final char[] volumeName;
    private final int clusterSize;
    private final long volumeSize;
    private final int reservedClusters;
    private final short createDate;
    private final short createTime;
    private short lastDefragmentationDate;
    private short lastDefragmentationTime;
    private int nextClusterIndex;
    private int freeClusters;
    private int nextRecordIndex;
    private int dufsTailSignature = 0x4A455442; // "JETB"

    public int getDufsNoseSignature() {
        return dufsNoseSignature;
    }

    public char[] getVolumeName() {
        return volumeName;
    }

    public int getClusterSize() {
        return clusterSize;
    }

    public long getVolumeSize() {
        return volumeSize;
    }

    public int getReservedClusters() {
        return reservedClusters;
    }

    public short getCreateDate() {
        return createDate;
    }

    public short getCreateTime() {
        return createTime;
    }

    public short getLastDefragmentationDate() {
        return lastDefragmentationDate;
    }

    public short getLastDefragmentationTime() {
        return lastDefragmentationTime;
    }

    public int getNextClusterIndex() {
        return nextClusterIndex;
    }

    public int getFreeClusters() {
        return freeClusters;
    }

    public int getNextRecordIndex() {
        return nextRecordIndex;
    }

    public int getDufsTailSignature() {
        return dufsTailSignature;
    }

    public void setLastDefragmentationDate(short lastDefragmentationDate) {
        this.lastDefragmentationDate = lastDefragmentationDate;
    }

    public void setLastDefragmentationTime(short lastDefragmentationTime) {
        this.lastDefragmentationTime = lastDefragmentationTime;
    }

    public void setNextClusterIndex(int nextClusterIndex) {
        this.nextClusterIndex = nextClusterIndex;
    }

    public void setFreeClusters(int freeClusters) {
        this.freeClusters = freeClusters;
    }

    public void setNextRecordIndex(int nextRecordIndex) {
        this.nextRecordIndex = nextRecordIndex;
    }

    public ReservedSpace(char[] volumeName, int clusterSize, long volumeSize) {
        this.volumeName = volumeName;
        this.clusterSize = clusterSize;
        this.volumeSize = VolumeHelperUtility.calculateVolumeSize(clusterSize, volumeSize);
        this.reservedClusters = VolumeHelperUtility.clustersAmount(clusterSize, volumeSize);
        this.createDate = DateUtility.dateToShort(LocalDate.now());
        this.createTime = DateUtility.timeToShort(LocalDateTime.now());
        this.lastDefragmentationDate = this.createDate;
        this.lastDefragmentationTime = this.createTime;
        this.nextClusterIndex = 1;
        this.freeClusters = reservedClusters - 1;
        this.nextRecordIndex = 1;
    }

    public ReservedSpace(int noseSignature, char[] volumeName, int clusterSize, long volumeSize, int reservedClusters,
                         short createDate, short createTime, short lastDefragmentationDate, short lastDefragmentationTime,
                         int nextClusterIndex, int freeClustersCount, int nextRecordIndex, int tailSignature) {
        this.dufsNoseSignature = noseSignature;
        this.volumeName = volumeName;
        this.clusterSize = clusterSize;
        this.volumeSize = volumeSize;
        this.reservedClusters = reservedClusters;
        this.createDate = createDate;
        this.createTime = createTime;
        this.lastDefragmentationDate = lastDefragmentationDate;
        this.lastDefragmentationTime = lastDefragmentationTime;
        this.nextClusterIndex = nextClusterIndex;
        this.freeClusters = freeClustersCount;
        this.nextRecordIndex = nextRecordIndex;
        this.dufsTailSignature = tailSignature;
    }

    public byte[] serialize() {
        final int bytesCount = 60;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesCount);
        buffer.putInt(dufsNoseSignature);
        for (int i = 0; i < 8; ++i) {
            if (i < volumeName.length) {
                buffer.putChar(volumeName[i]);
            } else {
                buffer.putChar('\u0000');
            }
        }
        buffer.putInt(clusterSize);
        buffer.putLong(volumeSize);
        buffer.putInt(reservedClusters);
        buffer.putShort(createDate);
        buffer.putShort(createTime);
        buffer.putShort(lastDefragmentationDate);
        buffer.putShort(lastDefragmentationTime);
        buffer.putInt(nextClusterIndex);
        buffer.putInt(freeClusters);
        buffer.putInt(nextRecordIndex);
        buffer.putInt(dufsTailSignature);
        buffer.position(0);
        byte[] tmp = new byte[bytesCount];
        buffer.get(tmp, 0, bytesCount);
        return tmp;
    }
}