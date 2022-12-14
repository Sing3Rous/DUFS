package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.model.Record;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.RecordOffsets;
import com.dufs.offsets.ReservedSpaceOffsets;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

public class VolumeIO {
    public static void initializeRootClusterIndexElement(RandomAccessFile volume) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        volume.writeInt(0xFFFFFFFF);    // mark root's first cluster as last cluster in chain
        volume.writeInt(0xFFFFFFFF);    // mark root's first cluster as first cluster in chain
        volume.writeInt(0);             // mark root's cluster's record index as 0
        volume.seek(defaultFilePointer);
    }

    public static void initializeRootRecord(RandomAccessFile volume, ReservedSpace reservedSpace) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 0));
        String rootName = new String(Arrays.copyOf(reservedSpace.getVolumeName(), 32));
        volume.writeChars(rootName);
        volume.writeShort(reservedSpace.getCreateDate());
        volume.writeShort(reservedSpace.getCreateTime());
        volume.writeInt(0);
        volume.writeShort(reservedSpace.getCreateDate());
        volume.writeShort(reservedSpace.getCreateTime());
        volume.writeLong(0);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeByte(0);
        volume.seek(defaultFilePointer);
    }

    public static ReservedSpace readReservedSpaceFromVolume(RandomAccessFile volume) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(ReservedSpaceOffsets.DUFS_NOSE_SIGNATURE_OFFSET);
        int noseSignature = volume.readInt();
        char[] volumeName = new char[8];
        for (int i = 0; i < 8; ++i) {
            volumeName[i] = volume.readChar();
        }
        int clusterSize = volume.readInt();
        long volumeSize = volume.readLong();
        int reservedClusters = volume.readInt();
        short createDate = volume.readShort();
        short createTime = volume.readShort();
        short lastDefragmentationDate = volume.readShort();
        short lastDefragmentationTime = volume.readShort();
        int nextClusterIndex = volume.readInt();
        int freeClusters = volume.readInt();
        int nextRecordIndex = volume.readInt();
        int tailSignature = volume.readInt();
        volume.seek(defaultFilePointer);
        return new ReservedSpace(noseSignature, volumeName, clusterSize, volumeSize, reservedClusters, createDate,
                createTime, lastDefragmentationDate, lastDefragmentationTime, nextClusterIndex,
                freeClusters, nextRecordIndex, tailSignature);
    }

    public static Record readRecordFromVolume(RandomAccessFile volume, ReservedSpace reservedSpace, int index) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, index));
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
        int parentDirectoryIndexOrderNumber = volume.readInt();
        byte isFile = volume.readByte();
        volume.seek(defaultFilePointer);
        return new Record(name, createDate, createTime, firstClusterIndex, lastEditDate, lastEditTime,
                size, parentDirectoryIndex, parentDirectoryIndexOrderNumber, isFile);
    }

    public static void readClusterFromVolume(RandomAccessFile volume, ReservedSpace reservedSpace, int index, byte[] buffer) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, index));
        volume.read(buffer);
        volume.seek(defaultFilePointer);
    }

    public static void writeRecordToVolume(RandomAccessFile volume, ReservedSpace reservedSpace, int index, Record record) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, index));
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
        volume.writeInt(record.getParentDirectoryIndexOrderNumber());
        volume.writeByte(record.getIsFile());
        volume.seek(defaultFilePointer);
    }

    public static void updateVolumeFreeClusters(RandomAccessFile volume, int freeClusters) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(ReservedSpaceOffsets.FREE_CLUSTERS_OFFSET);
        volume.writeInt(freeClusters);
        volume.seek(defaultFilePointer);
    }

    public static void updateVolumeNextClusterIndex(RandomAccessFile volume, int nextClusterIndex) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(ReservedSpaceOffsets.NEXT_CLUSTER_INDEX_OFFSET);
        volume.writeInt(nextClusterIndex);
        volume.seek(defaultFilePointer);
    }

    public static void updateVolumeNextRecordIndex(RandomAccessFile volume, int nextRecordIndex) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(ReservedSpaceOffsets.NEXT_RECORD_INDEX_OFFSET);
        volume.writeInt(nextRecordIndex);
        volume.seek(defaultFilePointer);
    }

    public static void updateVolumeLastDefragmentation(RandomAccessFile volume) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(ReservedSpaceOffsets.LAST_DEFRAGMENTATION_DATE_OFFSET);
        volume.writeShort(DateUtility.dateToShort(LocalDate.now()));
        volume.writeShort(DateUtility.timeToShort(LocalDateTime.now()));
        volume.seek(defaultFilePointer);
    }

    public static void updateRecordName(RandomAccessFile volume, ReservedSpace reservedSpace, int recordIndex, char[] name) throws IOException, DufsException {
        if (recordIndex == 0) {
            throw new DufsException("Root's record cannot be modified.");
        }
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex) + RecordOffsets.NAME_OFFSET);
        for (int i = 0; i < 32; ++i) {
            volume.writeChar(name[i]);
        }
        volume.seek(defaultFilePointer);
    }

    public static void updateRecordFirstClusterIndex(RandomAccessFile volume, ReservedSpace reservedSpace, int recordIndex,
                                                     int firstClusterIndex) throws DufsException, IOException {
        if (recordIndex == 0) {
            throw new DufsException("Root's record cannot be modified.");
        }
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex) + RecordOffsets.FIRST_CLUSTER_INDEX_OFFSET);
        volume.writeInt(firstClusterIndex);
        volume.seek(defaultFilePointer);
    }

    public static void updateRecordParentDirectory(RandomAccessFile volume, ReservedSpace reservedSpace, int recordIndex,
                                                   int parentDirectoryIndex, int parentDirectoryIndexOrderNumber) throws IOException, DufsException {
        if (recordIndex == 0) {
            throw new DufsException("Root's record cannot be modified.");
        }
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex) + RecordOffsets.PARENT_DIRECTORY_INDEX_OFFSET);
        volume.writeInt(parentDirectoryIndex);
        volume.writeInt(parentDirectoryIndexOrderNumber);
        volume.seek(defaultFilePointer);
    }

    public static void updateRecordParentDirectoryOrderNumber(RandomAccessFile volume, ReservedSpace reservedSpace, int recordIndex,
                                                              int parentDirectoryIndexOrderNumber) throws  IOException, DufsException {
        if (recordIndex == 0) {
            throw new DufsException("Root's record cannot be modified.");
        }
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex) + RecordOffsets.PARENT_DIRECTORY_INDEX_ORDER_NUMBER_OFFSET);
        volume.writeInt(parentDirectoryIndexOrderNumber);
        volume.seek(defaultFilePointer);
    }

    public static void updateRecordSize(RandomAccessFile volume, ReservedSpace reservedSpace, int recordIndex, long size) throws IOException, DufsException {
        if (recordIndex == 0) {
            throw new DufsException("Root's record cannot be modified.");
        }
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex) + RecordOffsets.SIZE_OFFSET);
        volume.writeLong(size);
        volume.seek(defaultFilePointer);
    }

    public static void updateRecordLastEdit(RandomAccessFile volume, ReservedSpace reservedSpace, int recordIndex) throws DufsException, IOException {
        if (recordIndex == 0) {
            throw new DufsException("Root's record cannot be modified.");
        }
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex) + RecordOffsets.LAST_EDIT_DATE_OFFSET);
        volume.writeShort(DateUtility.dateToShort(LocalDate.now()));
        volume.writeShort(DateUtility.timeToShort(LocalDateTime.now()));
        volume.seek(defaultFilePointer);
    }

    public static void cleanFileData(RandomAccessFile volume, ReservedSpace reservedSpace, int recordIndex) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        Record file = readRecordFromVolume(volume, reservedSpace, recordIndex);
        if (file.getIsFile() == 0) {
            throw new DufsException("Given record is not a file.");
        }
        int clusterIndex = file.getFirstClusterIndex();
        while (clusterIndex != 0xFFFFFFFF) {
            volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex));
            byte[] emptyCluster = new byte[reservedSpace.getClusterSize()];
            volume.write(emptyCluster);
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(clusterIndex));
            int prevClusterIndex = clusterIndex;
            clusterIndex = volume.readInt();
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(prevClusterIndex));
            volume.writeInt(0);
            volume.writeInt(0);
            volume.writeInt(0xFFFFFFFF);
        }
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(file.getFirstClusterIndex()));
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(recordIndex);
        volume.seek(defaultFilePointer);
    }
}