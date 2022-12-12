package com.dufs.model;

public class ReservedSpace {
    private int DUFSNoseSignature = 0x44554653;
    private long volumeName;
    private int reservedClusters;
    private short createDate;
    private short createTime;
    private short lastDefragmentationDate;
    private short lastDefragmentationTime;
    private int nextClusterIndex;
    private int freeClustersCount;
    private int nextRecordIndex;
    private int DUFSTailSignature = 0x4A455442;
}
