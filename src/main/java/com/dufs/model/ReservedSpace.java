package com.dufs.model;

import com.dufs.utility.DateUtility;
import com.dufs.utility.VolumeUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReservedSpace {
    private final static int DUFS_NOSE_SIGNATURE = 0x44554653; // "DUFS"
    private char[] volumeName = new char[8];
    private int clusterSize;
    private long volumeSize;
    private int reservedClusters;
    private short createDate;
    private short createTime;
    private short lastDefragmentationDate;
    private short lastDefragmentationTime;
    private int nextClusterIndex;
    private int freeClustersCount;
    private int nextRecordIndex;
    private final static int DUFS_TAIL_SIGNATURE = 0x4A455442; // "JETB"

    public ReservedSpace(char[] volumeName, int clusterSize, long volumeSize) {
        this.volumeName = volumeName;
        this.clusterSize = clusterSize;
        this.volumeSize = volumeSize;
        this.reservedClusters = VolumeUtility.clustersAmount(clusterSize, volumeSize);
        this.createDate = com.dufs.utility.DateUtility.dateToShort(LocalDate.now());
        this.createTime = DateUtility.timeToShort(LocalDateTime.now());
        this.lastDefragmentationDate = this.createDate;
        this.lastDefragmentationTime = this.createTime;
        this.nextClusterIndex = 0;
        this.freeClustersCount = reservedClusters;
        this.nextRecordIndex = 0;
    }
}
