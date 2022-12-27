package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.model.Record;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.ClusterIndexListOffsets;
import com.dufs.offsets.RecordListOffsets;
import com.dufs.offsets.RecordOffsets;
import com.dufs.offsets.ReservedSpaceOffsets;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

public class VolumeHelper {
    public static int howMuchClustersNeeds(ReservedSpace reservedSpace, long size) {
        return (int) Math.ceilDiv(size, reservedSpace.getClusterSize());
    }

    public static int howMuchClustersDirectoryTakes(RandomAccessFile volume, ReservedSpace reservedSpace, int directoryIndex) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, directoryIndex));
        int numberOfRecords = volume.readInt();
        int numberOfClusters = Math.max(1, (numberOfRecords * 4) / reservedSpace.getClusterSize());
        volume.seek(defaultFilePointer);
        return numberOfClusters;
    }

    public static long calculateVolumeSize(int clusterSize, long nettoVolumeSize) {
        int clustersAmount = clustersAmount(clusterSize, nettoVolumeSize);
        return ReservedSpaceOffsets.RESERVED_SPACE_SIZE
                + ((long) ClusterIndexListOffsets.CLUSTER_INDEX_ELEMENT_SIZE * clustersAmount)
                + ((long) RecordListOffsets.RECORD_SIZE * clustersAmount)
                + nettoVolumeSize;
    }

    public static int clustersAmount(int clusterSize, long volumeSize) {
        return ((int) Math.ceil((1.0 * volumeSize) / clusterSize));
    }

    public static boolean enoughSpace(ReservedSpace reservedSpace, long size) {
        return (reservedSpace.getFreeClusters() - VolumeHelper.howMuchClustersNeeds(reservedSpace, size)) >= 0;
    }

    public static boolean recordExists(RandomAccessFile volume, int firstClusterIndex) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(firstClusterIndex));
        int index = volume.readInt();
        volume.seek(defaultFilePointer);
        return (index != 0);
    }

    /*
     * checks if Record.createDate is equal to 0
     */
    public static boolean recordExists(RandomAccessFile volume, ReservedSpace reservedSpace, int recordIndex) throws  IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex) + RecordOffsets.CREATE_DATE_OFFSET);
        short date = volume.readShort();
        volume.seek(defaultFilePointer);
        return (date != 0);
    }

    /*
     * linear traverse through content in directory's cluster chain
     */
    public static boolean isNameUniqueInDirectory(RandomAccessFile volume, ReservedSpace reservedSpace,
                                                  int directoryIndex, char[] name, byte isFile) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, directoryIndex) + RecordOffsets.FIRST_CLUSTER_INDEX_OFFSET);
        int clusterIndex = volume.readInt();
        int recordIndex;
        do {
            volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex) + 4);
            recordIndex = volume.readInt();
            int counter = 0;
            while (recordIndex != 0 && counter < (reservedSpace.getClusterSize() / 4)) {
                Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, recordIndex);
                if (Arrays.equals(Arrays.copyOf(name, 32), record.getName()) && record.getIsFile() == isFile) {
                    return false;
                }
                recordIndex = volume.readInt();
                counter++;
            }
            clusterIndex = VolumeUtility.findNextClusterIndexInChain(volume, clusterIndex);
        } while (clusterIndex != -1);
        volume.seek(defaultFilePointer);
        return true;
    }

    public static boolean isDirectoryEmpty(RandomAccessFile volume, ReservedSpace reservedSpace, int directoryIndex) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, directoryIndex));
        int numberOfRecords = volume.readInt();
        volume.seek(defaultFilePointer);
        return (numberOfRecords == 0);
    }
}