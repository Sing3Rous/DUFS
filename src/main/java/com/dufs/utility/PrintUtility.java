package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.model.Record;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.RecordOffsets;

import java.io.IOException;
import java.io.RandomAccessFile;

public class PrintUtility {
    public static double bytes2GiB(long bytes) {
        return (bytes >> 30);
    }

    public static double bytes2MiB(long bytes) {
        return (bytes >> 20);
    }

    public static double bytes2KiB(long bytes) {
        return (bytes >> 10);
    }

    public static void printRecordsInDirectory(RandomAccessFile volume, ReservedSpace reservedSpace, int directoryIndex) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, directoryIndex) + RecordOffsets.FIRST_CLUSTER_INDEX_OFFSET);
        int firstClusterIndex = volume.readInt();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, firstClusterIndex));
        int recordsCount = volume.readInt();
        System.out.println("Records count: " + recordsCount);
        int clusterIndex = 0;
        int recordIndex;
        do {
            recordIndex = volume.readInt();
            int counter = 0;
            while (recordIndex != 0 && counter < (reservedSpace.getClusterSize() / 4)) {
                Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, recordIndex);
                printRecord(record);
                recordIndex = volume.readInt();
                counter++;
            }
            clusterIndex = VolumeUtility.findNextClusterIndexInChain(volume, clusterIndex);
        } while (clusterIndex != -1);
        volume.seek(defaultFilePointer);
    }

    public static void printRecord(Record record) {
        String name = new String(record.getName()).replace("\u0000", "");
        System.out.print(name + ", ");
        if (record.getIsFile() == 1) {
            System.out.print("file, ");
            System.out.print(record.getSize() + " bytes, ");
        } else {
            System.out.print("directory, ");
        }
        int[] createDate = DateUtility.shortToDate(record.getCreateDate());
        int[] createTime = DateUtility.shortToTime(record.getCreateTime());
        System.out.print("created " + createDate[2] + "." + createDate[1] + "." + createDate[0]
                + " at " + createTime[0] + ":" + createTime[1] + ":" + createTime[2] + ", ");
        int[] lastEditDate = DateUtility.shortToDate(record.getLastEditDate());
        int[] lastEditTime = DateUtility.shortToTime(record.getLastEditTime());
        System.out.print("last edited " + lastEditDate[2] + "." + lastEditDate[1] + "." + lastEditDate[0]
                + " at " + lastEditTime[0] + ":" + lastEditTime[1] + ":" + lastEditTime[2]);
        System.out.println();
    }

    public static void printRecordClusterChain(RandomAccessFile volume, int firstClusterIndex) throws IOException, DufsException {
        int clusterIndex = firstClusterIndex;
        System.out.print("Cluster chain: [ ");
        System.out.print(clusterIndex);
        while ((clusterIndex = VolumeUtility.findNextClusterIndexInChain(volume, clusterIndex)) != -1) {
            System.out.print(" -> ");
            System.out.print(clusterIndex);
        }
        System.out.println(" ]");
        System.out.println();
    }

    public static void dfsPrintRecords(RandomAccessFile volume, ReservedSpace reservedSpace,
                                       int directoryIndex, int depth) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, directoryIndex));
        int clusterIndex = directoryIndex;
        int recordIndex;
        long clusterPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, directoryIndex) + 4;
        do {
            volume.seek(clusterPosition);
            recordIndex = volume.readInt();
            int counter = 0;
            while (recordIndex != 0 && counter < (reservedSpace.getClusterSize() / 4)) {
                Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, recordIndex);
                String name = new String(record.getName()).replace("\u0000", "");
                for (int i = 0; i < depth; ++i) {
                    System.out.print("|\t");
                }
                System.out.println("|" + name);
                if (record.getIsFile() == 0) {
                    dfsPrintRecords(volume, reservedSpace, record.getFirstClusterIndex(), depth + 1);
                }
                recordIndex = volume.readInt();
            }
            clusterIndex = VolumeUtility.findNextClusterIndexInChain(volume, clusterIndex);
            clusterPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex);
        } while (clusterIndex != -1);
        volume.seek(defaultFilePointer);
    }

    public static void printRecords(RandomAccessFile volume, ReservedSpace reservedSpace) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        for (int i = 0; i < reservedSpace.getReservedClusters(); ++i) {
            Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, i);
            if (VolumeHelper.recordExists(volume, reservedSpace, i)) {
                System.out.print("#" + i + ", ");
                if (record.getIsFile() == 1) {
                    System.out.print("(FILE)");
                } else {
                    System.out.print("(DIR)");
                }
                System.out.println(":");
                printRecord(record);
                printRecordClusterChain(volume, record.getFirstClusterIndex());
            }
        }
        volume.seek(defaultFilePointer);
    }
}