package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.model.Record;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.ClusterIndexListOffsets;
import com.dufs.offsets.RecordListOffsets;
import com.dufs.offsets.ReservedSpaceOffsets;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VolumeUtility {
    public static int clustersAmount(int clusterSize, long volumeSize) {
        return ((int) Math.ceil((1.0 * volumeSize) / clusterSize));
    }

    public static ReservedSpace readReservedSpaceFromVolume(RandomAccessFile volume) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(ReservedSpaceOffsets.DUFS_NOSE_SIGNATURE_OFFSET);
        int noseSignature = volume.readInt();
        volume.seek(ReservedSpaceOffsets.VOLUME_NAME_OFFSET);
        char[] volumeName = new char[8];
        for (int i = 0; i < 8; ++i) {
            volumeName[i] = volume.readChar();
        }
        volume.seek(ReservedSpaceOffsets.CLUSTER_SIZE_OFFSET);
        int clusterSize = volume.readInt();
        volume.seek(ReservedSpaceOffsets.VOLUME_SIZE_OFFSET);
        long volumeSize = volume.readLong();
        volume.seek(ReservedSpaceOffsets.RESERVED_CLUSTERS_OFFSET);
        int reservedClusters = volume.readInt();
        volume.seek(ReservedSpaceOffsets.CREATE_DATE_OFFSET);
        short createDate = volume.readShort();
        volume.seek(ReservedSpaceOffsets.CREATE_TIME_OFFSET);
        short createTime = volume.readShort();
        volume.seek(ReservedSpaceOffsets.LAST_DEFRAGMENTATION_DATE_OFFSET);
        short lastDefragmentationDate = volume.readShort();
        volume.seek(ReservedSpaceOffsets.LAST_DEFRAGMENTATION_TIME_OFFSET);
        short lastDefragmentationTime = volume.readShort();
        volume.seek(ReservedSpaceOffsets.NEXT_CLUSTER_INDEX_OFFSET);
        int nextClusterIndex = volume.readInt();
        volume.seek(ReservedSpaceOffsets.FREE_CLUSTERS_OFFSET);
        int freeClusters= volume.readInt();
        volume.seek(ReservedSpaceOffsets.NEXT_RECORD_INDEX_OFFSET);
        int nextRecordIndex = volume.readInt();
        volume.seek(ReservedSpaceOffsets.DUFS_TAIL_SIGNATURE_OFFSET);
        int tailSignature = volume.readInt();
        volume.seek(defaultFilePointer);
        return new ReservedSpace(noseSignature, volumeName, clusterSize, volumeSize, reservedClusters, createDate,
                createTime, lastDefragmentationDate, lastDefragmentationTime, nextClusterIndex,
                freeClusters, nextRecordIndex, tailSignature);
    }

    public static Record readRecordFromVolume(RandomAccessFile volume, ReservedSpace reservedSpace, int index) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(calculateRecordPosition(reservedSpace, index));
        char[] name = new char[32];
        for (int i = 0; i < 32; ++i) {
            name[i] = volume.readChar();
        }
        short createDate = volume.readShort();
        short createTime = volume.readShort();
        int firstClusterIndex = volume.readInt();
        short lastEditDate = volume.readShort();
        short lastEditTime = volume.readShort();
        long size = volume.readLong();
        int parentDirectoryIndex = volume.readInt();
        byte isFile = volume.readByte();
        volume.seek(defaultFilePointer);
        return new Record(name, createDate, createTime, firstClusterIndex,
                lastEditDate, lastEditTime, size, parentDirectoryIndex, isFile);
    }

    public static void writeRecordToVolume(RandomAccessFile volume, ReservedSpace reservedSpace, int index, Record record) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(calculateRecordPosition(reservedSpace, index));
        for (int i = 0; i < 32; ++i) {
            volume.writeChar(record.getName()[i]);
        }
        volume.writeShort(record.getCreateDate());
        volume.writeShort(record.getCreateTime());
        volume.writeInt(record.getFirstClusterIndex());
        volume.writeShort(record.getLastEditDate());
        volume.writeShort(record.getLastEditTime());
        volume.writeLong(record.getSize());
        volume.writeInt(record.getParentDirectoryIndex());
        volume.writeByte(record.getIsFile());
        volume.seek(defaultFilePointer);
    }

    public static void allocateCluster(RandomAccessFile volume, int index) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(calculateClusterIndexPosition(index));
        volume.writeInt(0xFFFFFFFF);    // ClusterIndexElement.nextClusterIndex
        volume.writeInt(0xFFFFFFFF);    // ClusterIndexElement.prevClusterIndex
        volume.seek(defaultFilePointer);
    }

    /*
     * currently it uses linear search, which is bad.
     * architecturally it could be remade on b-trees-like data structure and find record by O(logn) through binary search
    */
    public static int findDirectoryIndex(RandomAccessFile volume, ReservedSpace reservedSpace, String path) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        String[] records = Parser.parsePath(path);
        if (records.length == 0 || !Arrays.equals(records[0].toCharArray(), reservedSpace.getVolumeName())) {
            throw new DufsException("Given path is not correct");
        }
        int clusterIndex = 0;
        int recordIndex = 0; // unnecessary initialization
        for (int i = 0; i < records.length; ++i) {
            volume.seek(calculateClusterPosition(reservedSpace, clusterIndex));
            recordIndex = volume.readInt();
            do {
                Record record = readRecordFromVolume(volume, reservedSpace, recordIndex);
                if (Arrays.equals(records[0].toCharArray(), record.getName())
                        && record.getIsFile() == 0) {
                    clusterIndex = record.getFirstClusterIndex();
                    break;
                }
                recordIndex = volume.readInt();
            } while (recordIndex != 0);
            if (clusterIndex == 0) {
                throw new DufsException("Given path does not exist.");
            }
        }
        volume.seek(defaultFilePointer);
        return recordIndex;
    }

    public static int findFileIndex(RandomAccessFile volume, ReservedSpace reservedSpace, String path) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        int directoryIndex = findDirectoryIndex(volume, reservedSpace, Parser.joinPath(Parser.parsePathBeforeFile(path)));
        String fileName = Parser.parseFileNameInPath(path);
        volume.seek(calculateClusterPosition(reservedSpace, directoryIndex));
        int clusterIndex = 0;
        int recordIndex = volume.readInt();
        do {
            Record record = readRecordFromVolume(volume, reservedSpace, recordIndex);
            if (Arrays.equals(fileName.toCharArray(), record.getName())
                    && record.getIsFile() == 1) {
                clusterIndex = record.getFirstClusterIndex();
                break;
            }
            recordIndex = volume.readInt();
        } while (recordIndex != 0);
        if (clusterIndex == 0) {
            throw new DufsException("Given file does not exist.");
        }
        volume.seek(defaultFilePointer);
        return recordIndex;
    }

    /*
     * runs through the cluster chain (starting from given in ReservedSpace index) and returns first met 0;
     */
    public static int findNextFreeClusterIndex(RandomAccessFile volume, ReservedSpace reservedSpace) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        int currentClusterIndex = reservedSpace.getNextClusterIndex();
        long currentClusterIndexPosition = calculateClusterIndexPosition(currentClusterIndex);
        int nextFreeClusterIndex = currentClusterIndex - 1;
        int clusterIndexElementData;
        do {
            volume.seek(currentClusterIndexPosition);
            nextFreeClusterIndex++;
            clusterIndexElementData = volume.readInt(); // read 4 bytes of ClusterIndexElement.nextClusterIndex
            currentClusterIndexPosition += 4;           // skip 4 bytes of ClusterIndexElement.prevClusterIndex
            // TODO: fix cyclic run-through
        } while (clusterIndexElementData != 0);
        volume.seek(defaultFilePointer);
        return nextFreeClusterIndex;
    }

    // could load RAM very much, so maybe it should be reconsidered and reimplemented
    private static int[] clustersChainIndexes(RandomAccessFile volume, ReservedSpace reservedSpace, int firstClusterIndex) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(calculateClusterPosition(reservedSpace, firstClusterIndex));
        List<Integer> clusters = new ArrayList<>();
        int index = volume.readInt();
        do {
            clusters.add(index);
            volume.seek(calculateClusterIndexPosition(index));
            index = volume.readInt();
        } while (index != 0xFFFFFFFF);
        volume.seek(defaultFilePointer);
        return clusters.stream().mapToInt(Integer::intValue).toArray();
    }

    private static long calculateRecordPosition(ReservedSpace reservedSpace, int recordIndex) {
        return calculateRecordListOffset(reservedSpace) + (long) RecordListOffsets.RECORD_SIZE * recordIndex;
    }

    private static long calculateClusterIndexPosition(int clusterIndex) {
        return ClusterIndexListOffsets.CLUSTER_INDEX_LIST_OFFSET
                + (long) ClusterIndexListOffsets.CLUSTER_INDEX_ELEMENT_SIZE * clusterIndex;
    }

    private static long calculateClusterPosition(ReservedSpace reservedSpace, int clusterIndex) {
        return calculateClustersAreaOffset(reservedSpace) + (long) reservedSpace.getClusterSize() * clusterIndex;
    }

    // maybe could be made private
    public static long calculateRecordListOffset(ReservedSpace reservedSpace) {
        return ClusterIndexListOffsets.CLUSTER_INDEX_LIST_OFFSET
                + (long) reservedSpace.getReservedClusters() * ClusterIndexListOffsets.CLUSTER_INDEX_ELEMENT_SIZE;
    }

    private static long calculateClustersAreaOffset(ReservedSpace reservedSpace) {
        return calculateRecordListOffset(reservedSpace)
                + (long) RecordListOffsets.RECORD_SIZE * reservedSpace.getReservedClusters();
    }

    public static boolean enoughSpace(ReservedSpace reservedSpace, long size) {
        return reservedSpace.getFreeClusters() - howMuchClustersNeeds(reservedSpace, size) > 0;
    }

    private static int howMuchClustersNeeds(ReservedSpace reservedSpace, long size) {
        return (int) Math.ceil(1.0 * size / reservedSpace.getClusterSize());
    }
}
