package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;
import com.dufs.model.Record;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.RecordOffsets;
import com.dufs.offsets.ReservedSpaceOffsets;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class VolumeIOTest {

    private static Dufs dufs;
    private static ReservedSpace reservedSpace;
    private static File file;

    @BeforeEach
    void init() throws IOException, DufsException {
        file = new File("vol.DUFS");
        dufs = new Dufs();
        dufs.mountVolume(file.getName(), 4096, 4096000);
        reservedSpace = VolumeIO.readReservedSpaceFromVolume(dufs.getVolume());
    }

    @AfterEach
    void deleteFile() throws IOException, DufsException {
        dufs.closeVolume();
        file.delete();
    }

    @Test   // no need to call VolumeIO.initializeRootClusterIndexElement() directly because it was already called in Dufs.mountVolume()
    void initializeRootCluster() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        assertEquals(0xFFFFFFFF, volume.readInt()); // check ClusterIndexElement.nextClusterIndex
        assertEquals(0xFFFFFFFF, volume.readInt()); // check ClusterIndexElement.prevClusterIndex
        assertEquals(0, volume.readInt());          // check ClusterIndexElement.recordIndex
    }

    @Test   // no need to call VolumeIO.initializeRootRecord() directly because it was already called in Dufs.mountVolume()
    void initializeRootRecord() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 0));
        char[] recordName = new char[32];
        for (int i = 0; i < 32; ++i) {
            recordName[i] = volume.readChar();
        }
        assertEquals(new String(Arrays.copyOf("vol.DUFS".toCharArray(), 32)), new String(recordName));
        short date = DateUtility.dateToShort(LocalDate.now());
        assertEquals(date, volume.readShort());
        short time = DateUtility.timeToShort(LocalDateTime.now());
        assertEquals(time, volume.readShort());
        assertEquals(0, volume.readInt());
        assertEquals(date, volume.readShort());
        assertEquals(time, volume.readShort());
        assertEquals(0, volume.readLong());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(0, volume.readByte());
    }

    @Test
    void readReservedSpaceFromVolume() {
        // readReservedSpaceFromVolume is already called in init(), result keeps in reservedSpace variable
        assertEquals(0x44554653, reservedSpace.getDufsNoseSignature());
        assertEquals(new String(Arrays.copyOf("vol.DUFS".toCharArray(), 8)), new String(reservedSpace.getVolumeName()));
        assertEquals(4096, reservedSpace.getClusterSize());
        assertEquals(4201060, reservedSpace.getVolumeSize());
        assertEquals(1000, reservedSpace.getReservedClusters());
        short date = DateUtility.dateToShort(LocalDate.now());
        assertEquals(date, reservedSpace.getCreateDate());
        short time = DateUtility.timeToShort(LocalDateTime.now());
        assertEquals(time, reservedSpace.getCreateTime());
        assertEquals(date, reservedSpace.getLastDefragmentationDate());
        assertEquals(time, reservedSpace.getLastDefragmentationTime());
        assertEquals(1, reservedSpace.getNextClusterIndex());
        assertEquals(999, reservedSpace.getFreeClusters());
        assertEquals(1, reservedSpace.getNextRecordIndex());
        assertEquals(0x4A455442, reservedSpace.getDufsTailSignature());
    }

    @Test
    void readRecordFromVolume() throws IOException {
        Record record = VolumeIO.readRecordFromVolume(dufs.getVolume(), reservedSpace, 0);
        assertEquals(new String(Arrays.copyOf("vol.DUFS".toCharArray(), 32)),
                new String(Arrays.copyOf(record.getName(), 32)));
        short date = DateUtility.dateToShort(LocalDate.now());
        assertEquals(date, record.getCreateDate());
        short time = DateUtility.timeToShort(LocalDateTime.now());
        assertEquals(time, record.getCreateTime());
        assertEquals(0, record.getFirstClusterIndex());
        assertEquals(date, record.getLastEditDate());
        assertEquals(time, record.getLastEditTime());
        assertEquals(0, record.getSize());
        assertEquals(0xFFFFFFFF, record.getParentDirectoryIndex());
        assertEquals(0xFFFFFFFF, record.getParentDirectoryIndexOrderNumber());
        assertEquals((byte) 0, record.getIsFile());
    }

    @Test
    void readClusterFromVolume() throws IOException, DufsException {
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "file1", (byte) 1);
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "folder1", (byte) 0);
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "file2", (byte) 1);
        byte[] cluster = new byte[reservedSpace.getClusterSize()];
        VolumeIO.readClusterFromVolume(dufs.getVolume(), reservedSpace, 0, cluster);
        ByteBuffer bb = ByteBuffer.wrap(cluster);
        assertEquals(3, bb.getInt());   // 3 records in cluster
        assertEquals(1, bb.getInt());   // 1st record's recordIndex
        assertEquals(2, bb.getInt());   // 2nd record's recordIndex
        assertEquals(3, bb.getInt());   // 3rd record's recordIndex
    }

    @Test
    void writeRecordToVolume() throws IOException {
        Record record = new Record("new record".toCharArray(), 1, 0, 0, (byte) 1);
        VolumeIO.writeRecordToVolume(dufs.getVolume(), reservedSpace, 1, record);
        Record writtenRecord = VolumeIO.readRecordFromVolume(dufs.getVolume(), reservedSpace, 1);
        assertEquals(new String(Arrays.copyOf(record.getName(), 32)),
                new String(Arrays.copyOf(writtenRecord.getName(), 32)));
        assertEquals(record.getCreateDate(), writtenRecord.getCreateDate());
        assertEquals(record.getCreateTime(), writtenRecord.getCreateTime());
        assertEquals(record.getFirstClusterIndex(), writtenRecord.getFirstClusterIndex());
        assertEquals(record.getLastEditDate(), writtenRecord.getLastEditDate());
        assertEquals(record.getLastEditTime(), writtenRecord.getLastEditTime());
        assertEquals(record.getSize(), writtenRecord.getSize());
        assertEquals(record.getParentDirectoryIndex(), writtenRecord.getParentDirectoryIndex());
        assertEquals(record.getParentDirectoryIndexOrderNumber(), writtenRecord.getParentDirectoryIndexOrderNumber());
        assertEquals(record.getIsFile(), writtenRecord.getIsFile());
    }

    @Test
    void updateVolumeFreeClusters() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        int freeClustersCount = 65537;
        VolumeIO.updateVolumeFreeClusters(volume, freeClustersCount);
        volume.seek(ReservedSpaceOffsets.FREE_CLUSTERS_OFFSET);
        assertEquals(freeClustersCount, volume.readInt());
    }

    @Test
    void updateVolumeNextClusterIndex() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        int nextClusterIndex = 65537;
        VolumeIO.updateVolumeNextClusterIndex(volume, nextClusterIndex);
        volume.seek(ReservedSpaceOffsets.NEXT_CLUSTER_INDEX_OFFSET);
        assertEquals(nextClusterIndex, volume.readInt());
    }

    @Test
    void updateVolumeNextRecordIndex() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        int nextRecordIndex = 65537;
        VolumeIO.updateVolumeNextRecordIndex(volume, nextRecordIndex);
        volume.seek(ReservedSpaceOffsets.NEXT_RECORD_INDEX_OFFSET);
        assertEquals(nextRecordIndex, volume.readInt());
    }

    @Test
    void updateVolumeLastDefragmentation() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        VolumeIO.updateVolumeLastDefragmentation(volume);
        volume.seek(ReservedSpaceOffsets.LAST_DEFRAGMENTATION_DATE_OFFSET);
        assertEquals(DateUtility.dateToShort(LocalDate.now()), volume.readShort());
        assertEquals(DateUtility.timeToShort(LocalDateTime.now()), volume.readShort());
    }

    @Test
    void updateRecordName_renameRoot() {
        assertEquals("Root's record cannot be modified.",
                assertThrows(DufsException.class,
                        () -> VolumeIO.updateRecordName(dufs.getVolume(), reservedSpace, 0,
                                Mockito.anyString().toCharArray())).getMessage());
    }

    @Test
    void updateRecordName() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "file1", (byte) 1);
        VolumeIO.updateRecordName(volume, reservedSpace, 1, Arrays.copyOf("updatedFile1Name".toCharArray(), 32));
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1) + RecordOffsets.NAME_OFFSET);
        char[] recordName = new char[32];
        for (int i = 0; i < 32; ++i) {
            recordName[i] = volume.readChar();
        }
        assertEquals(new String(Arrays.copyOf("updatedFile1Name".toCharArray(), 32)),
                new String(recordName));
    }

    @Test
    void updateRecordFirstClusterIndex_updateRoot() {
        assertEquals("Root's record cannot be modified.",
                assertThrows(DufsException.class,
                        () -> VolumeIO.updateRecordFirstClusterIndex(dufs.getVolume(), reservedSpace, 0,
                                Mockito.anyInt())).getMessage());
    }

    @Test
    void updateRecordFirstClusterIndex() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "file1", (byte) 1);
        VolumeIO.updateRecordFirstClusterIndex(volume, reservedSpace, 1, 6);
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1) + RecordOffsets.FIRST_CLUSTER_INDEX_OFFSET);
        assertEquals(6, volume.readInt());
    }

    @Test
    void updateRecordParentDirectory_updateRoot() {
        assertEquals("Root's record cannot be modified.",
                assertThrows(DufsException.class,
                        () -> VolumeIO.updateRecordParentDirectory(dufs.getVolume(), reservedSpace, 0,
                                Mockito.anyInt(), Mockito.anyInt())).getMessage());
    }

    @Test
    void updateRecordParentDirectory() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "file1", (byte) 1);
        VolumeIO.updateRecordParentDirectory(volume, reservedSpace, 1,
                65537, 131071);
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1) + RecordOffsets.PARENT_DIRECTORY_INDEX_OFFSET);
        assertEquals(65537, volume.readInt());
        assertEquals(131071, volume.readInt());
    }

    @Test
    void updateRecordParentDirectoryOrderNumber_updateRoot() {
        assertEquals("Root's record cannot be modified.",
                assertThrows(DufsException.class,
                        () -> VolumeIO.updateRecordParentDirectoryOrderNumber(dufs.getVolume(), reservedSpace, 0,
                                Mockito.anyInt())).getMessage());
    }

    @Test
    void updateRecordParentDirectoryOrderNumber() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "file1", (byte) 1);
        VolumeIO.updateRecordParentDirectoryOrderNumber(volume, reservedSpace, 1, 131071);
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1) + RecordOffsets.PARENT_DIRECTORY_INDEX_ORDER_NUMBER_OFFSET);
        assertEquals(131071, volume.readInt());
    }

    @Test
    void updateRecordSize_updateRoot() {
        assertEquals("Root's record cannot be modified.",
                assertThrows(DufsException.class,
                        () -> VolumeIO.updateRecordSize(dufs.getVolume(), reservedSpace, 0,
                                Mockito.anyInt())).getMessage());
    }

    @Test
    void updateRecordSize() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "file1", (byte) 1);
        VolumeIO.updateRecordSize(volume, reservedSpace, 1, 262145);
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1) + RecordOffsets.SIZE_OFFSET);
        assertEquals(262145, volume.readLong());
    }

    @Test
    void updateRecordLastEdit_updateRoot() {
        assertEquals("Root's record cannot be modified.",
                assertThrows(DufsException.class,
                        () -> VolumeIO.updateRecordLastEdit(dufs.getVolume(), reservedSpace, 0)).getMessage());
    }

    @Test
    void updateRecordLastEdit() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "record", (byte) 1);
        VolumeIO.updateRecordLastEdit(volume, reservedSpace, 1);
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1) + RecordOffsets.LAST_EDIT_DATE_OFFSET);
        assertEquals(DateUtility.dateToShort(LocalDate.now()), volume.readShort());
        assertEquals(DateUtility.timeToShort(LocalDateTime.now()), volume.readShort());
    }

    @Test
    void cleanFileData_wrongIndex() throws IOException, DufsException {
        dufs.createRecord("vol.DUFS", "folder", (byte) 0);
        assertEquals("Given record is not a file.",
                assertThrows(DufsException.class,
                        () -> VolumeIO.cleanFileData(dufs.getVolume(), reservedSpace, 1)).getMessage());
    }

    @Test
    void cleanFileData() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "file", (byte) 1);
        byte[] content = new byte[1024];
        for (int i = 0; i < 1024; ++i) {
            content[i] = (byte) ((i + 4) ^ 42 << 2);
        }
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        volume.write(content);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(1);
        VolumeIO.cleanFileData(volume, reservedSpace, 1);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] clusterRead = new byte[reservedSpace.getClusterSize()];
        byte[] emptyCluster = new byte[reservedSpace.getClusterSize()];
        volume.read(clusterRead);
        assertArrayEquals(emptyCluster, clusterRead);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(1, volume.readInt());
    }

    @Test
    void cleanFileData_moreThanOneCluster() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "file", (byte) 1);
        byte[] content = new byte[8000];
        for (int i = 0; i < 8000; ++i) {
            content[i] = (byte) ((i + 4) ^ 42 << 2);
        }
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        volume.write(content);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        volume.writeInt(2);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(1);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(1);
        volume.writeInt(1);
        VolumeIO.cleanFileData(volume, reservedSpace, 1);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] clusterRead = new byte[reservedSpace.getClusterSize()];
        byte[] emptyCluster = new byte[reservedSpace.getClusterSize()];
        volume.read(clusterRead);
        assertArrayEquals(emptyCluster, clusterRead);
        volume.read(clusterRead);
        assertArrayEquals(emptyCluster, clusterRead);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(1, volume.readInt());
        assertEquals(0, volume.readInt());
        assertEquals(0, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
    }
}