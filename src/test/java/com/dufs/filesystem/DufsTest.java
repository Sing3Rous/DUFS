package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;
import com.dufs.model.Record;
import com.dufs.model.RecordList;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.RecordListOffsets;
import com.dufs.offsets.RecordOffsets;
import com.dufs.offsets.ReservedSpaceOffsets;
import com.dufs.utility.DateUtility;
import com.dufs.utility.VolumeIO;
import com.dufs.utility.VolumePointerUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.*;
import java.nio.file.FileSystems;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DufsTest {
    private static Dufs dufs;
    private static ReservedSpace reservedSpace;
    private static File file;

    @BeforeEach
    void init() throws IOException, DufsException {
        file = new File("vol.DUFS");
        dufs = new Dufs();
        dufs.mountVolume(file.getName(), 4096, 40960000);
        reservedSpace = VolumeIO.readReservedSpaceFromVolume(dufs.getVolume());
    }

    @AfterEach
    void deleteFile() throws IOException {
        dufs.closeVolume();
        file.delete();
    }

    @Test
    void mountVolume_fileExistence() {
        assertEquals("Volume with such name already exists in this directory.",
                assertThrows(DufsException.class,
                        () -> dufs.mountVolume(file.getName(), Mockito.anyInt(), Mockito.anyLong())).getMessage());
    }

    @Test
    void mountVolume_nameLength(){
        assertEquals("Volume name length has exceeded the limit.",
                assertThrows(DufsException.class,
                        () -> dufs.mountVolume("volume.DUFS", Mockito.anyInt(), Mockito.anyLong())).getMessage());
    }
    @Test
    void mountVolume_nameProhibitedSymbols() {
        assertEquals("Volume name contains prohibited symbols.",
                assertThrows(DufsException.class,
                        () -> dufs.mountVolume("v*.DUFS", Mockito.anyInt(), Mockito.anyLong())).getMessage());
    }

    @Test
    void mountVolume_volumeSize() {
        assertEquals("Volume size is too big.",
                assertThrows(DufsException.class,
                        () -> dufs.mountVolume(Mockito.anyString(), Mockito.anyInt(), (long) (1.1e12 + 1))).getMessage());
    }

    @Test
    void mountVolume_clusterSizeDivisibleBy4() {
        assertEquals("Cluster size cannot be divided by 4 directly.",
                assertThrows(DufsException.class,
                        () -> dufs.mountVolume(Mockito.anyString(), (4 - 1), Mockito.anyLong())).getMessage());
    }

    @Test
    void mountVolume() throws IOException, DufsException {
        final String volumeName = "tmp.DUFS";
        Dufs dufsToMount = new Dufs();
        File fileToMount = new File(volumeName);
        dufsToMount.mountVolume(volumeName, 4096, 4096000);
        assertTrue(fileToMount.exists());
        RandomAccessFile volume = new RandomAccessFile(fileToMount, "rw");
        assertEquals(4197060, fileToMount.length());
        Record rootRecord = VolumeIO.readRecordFromVolume(volume, VolumeIO.readReservedSpaceFromVolume(volume), 0);
        assertEquals(0xFFFFFFFF, rootRecord.getParentDirectoryIndex());
        assertEquals(new String(Arrays.copyOf("tmp.DUFS".toCharArray(), 32)),
                new String(rootRecord.getName()));
        volume.close();
        dufsToMount.closeVolume();
        fileToMount.delete();
    }

    @Test
    void attachVolume_missingVolume() throws IOException {
        dufs.closeVolume();
        file.delete();
        assertEquals("There is no volume with such name in this directory.",
                assertThrows(DufsException.class,
                        () -> dufs.attachVolume(Mockito.anyString())).getMessage());
    }

    @Test
    void attachVolume_signatureCheckupFail() throws IOException {
        dufs.closeVolume();
        file.delete();
        final String volumeName = "vol.FS";
        File notDufsFile = new File("vol.FS");
        RandomAccessFile notDufsVolume = new RandomAccessFile(notDufsFile, "rw");
        notDufsVolume.setLength(4096000);
        assertEquals("Volume signature does not match.",
                assertThrows(DufsException.class,
                        () -> dufs.attachVolume(volumeName)).getMessage());
        dufs.closeVolume();
        notDufsVolume.close();
        notDufsFile.delete();
    }

    @Test
    void createRecord_volumeNull() {
        Dufs nullVolumeDufs = new Dufs();
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class,
                        () -> nullVolumeDufs.createRecord("vol.DUFS", Mockito.anyString(), Mockito.anyByte())).getMessage());
    }

    @Test
    void createRecord_nameLength() {
        assertEquals("Directory name length has exceeded the limit.",
                assertThrows(DufsException.class,
                        () -> dufs.createRecord("vol.DUFS",
                                "123456789123456789123456789123456", (byte) 0)).getMessage());
    }

    @Test
    void createRecord_nameProhibitedSymbols() {
        assertEquals("File name contains prohibited symbols.",
                assertThrows(DufsException.class,
                        () -> dufs.createRecord("vol.DUFS",
                                "123*", (byte) 1)).getMessage());
    }

    @Test
    void createRecord_duplicate() throws IOException, DufsException {
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        assertEquals("File with such name already contains in this path.",
                assertThrows(DufsException.class,
                        () -> dufs.createRecord("vol.DUFS", "record", (byte) 1)).getMessage());
    }

    @Test
    void createRecord_recordSize() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        // set free clusters as 0
        volume.seek(ReservedSpaceOffsets.FREE_CLUSTERS_OFFSET);
        volume.writeInt(0);
        assertEquals("Not enough space in the volume to create new File.",
                assertThrows(DufsException.class,
                        () -> dufs.createRecord("vol.DUFS", Mockito.anyString(), (byte) 1)).getMessage());
    }

    @Test
    void createRecord() throws IOException, DufsException {
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        RandomAccessFile volume = dufs.getVolume();
        // check if file exists in DUFS
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1)
                + RecordOffsets.CREATE_DATE_OFFSET);
        assertEquals(DateUtility.dateToShort(LocalDate.now()), volume.readShort());
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1)
                + RecordOffsets.NAME_OFFSET);
        char[] recordName = new char[32];
        for (int i = 0; i < 32; ++i) {
            recordName[i] = volume.readChar();
        }
        assertEquals(new String(Arrays.copyOf("record".toCharArray(), 32)), new String(recordName));
        // check if reserved space is updated
        volume.seek(ReservedSpaceOffsets.NEXT_CLUSTER_INDEX_OFFSET);
        assertEquals(2, volume.readInt());
        volume.seek(ReservedSpaceOffsets.NEXT_RECORD_INDEX_OFFSET);
        assertEquals(2, volume.readInt());
        volume.seek(ReservedSpaceOffsets.FREE_CLUSTERS_OFFSET);
        assertEquals(9998, volume.readInt());
        // check if cluster chain is created
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        // check if record contains in parent's directory cluster
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        assertEquals(1, volume.readInt());  // number of records in directory
        assertEquals(1, volume.readInt());  // first record in directory
    }

    @Test
    void writeFile_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        File tmpFile = new File("tmp.txt");
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class,
                        () -> nullVolumeDufs.writeFile("vol.DUFS", tmpFile)).getMessage());
    }

    @Test
    void writeFile_fileSize() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content = new byte[40960000];
        volume.write(content);
        assertEquals("Not enough space in the volume to write this content in file.",
                assertThrows(DufsException.class,
                        () -> dufs.writeFile("vol.DUFS", file)).getMessage());
        volume.close();
    }

    @Test
    void writeFile_nullFile() {
        File tmp = new File("tmp");
        assertEquals("Given path does not exist.",
                assertThrows(DufsException.class,
                        () -> dufs.writeFile("vol.DUFS"
                                + FileSystems.getDefault().getSeparator() + "record", tmp)).getMessage());
    }

    @Test
    void writeFile() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content = new byte[1024];
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        tmpRAF.setLength(1024);
        tmpRAF.write(content);
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        dufs.writeFile("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", tmpFile);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] contentRead = new byte[1024];
        volume.read(contentRead);
        assertArrayEquals(contentRead, content);
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void writeFile_moreThanOneCluster() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content1 = new byte[4096];
        byte[] content2 = new byte[4096];
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        tmpRAF.setLength(8192);
        tmpRAF.write(content1);
        tmpRAF.write(content2);
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        dufs.writeFile("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", tmpFile);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] contentRead = new byte[4096];
        volume.read(contentRead);
        assertArrayEquals(contentRead, content1);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 2));
        volume.read(contentRead);
        assertArrayEquals(contentRead, content2);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        assertEquals(2, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(3, volume.readInt());
        assertEquals(1, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(2, volume.readInt());
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void appendFile_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        File tmpFile = new File("tmp.txt");
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class,
                        () -> nullVolumeDufs.appendFile("vol.DUFS", tmpFile)).getMessage());
    }

    @Test
    void appendFile_fileSize() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content = new byte[40960000];
        volume.write(content);
        assertEquals("Not enough space in the volume to write this content in file.",
                assertThrows(DufsException.class,
                        () -> dufs.appendFile("vol.DUFS", file)).getMessage());
        volume.close();
    }

    @Test
    void appendFile_nullFile() {
        File tmp = new File("tmp");
        assertEquals("Given path does not exist.",
                assertThrows(DufsException.class,
                        () -> dufs.appendFile("vol.DUFS"
                                + FileSystems.getDefault().getSeparator() + "record", tmp)).getMessage());
    }

    @Test
    void appendFile() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content = new byte[1024];
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        tmpRAF.setLength(1024);
        tmpRAF.write(content);
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        dufs.appendFile("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", tmpFile);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] contentRead = new byte[1024];
        volume.read(contentRead);
        assertArrayEquals(contentRead, content);
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void appendFile_moreThanOneCluster() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content1 = new byte[4096];
        byte[] content2 = new byte[4096];
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        tmpRAF.setLength(8192);
        tmpRAF.write(content1);
        tmpRAF.write(content2);
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        dufs.appendFile("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", tmpFile);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] contentRead = new byte[4096];
        volume.read(contentRead);
        assertArrayEquals(contentRead, content1);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 2));
        volume.read(contentRead);
        assertArrayEquals(contentRead, content2);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        assertEquals(2, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(3, volume.readInt());
        assertEquals(1, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(2, volume.readInt());
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void appendFile_appendAfterAppend() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content1 = new byte[4096];
        byte[] content2 = new byte[4096];
        File tmpFile1 = new File("tmp");
        RandomAccessFile tmpRAF1 = new RandomAccessFile(tmpFile1, "rw");
        File tmpFile2 = new File("tmp");
        RandomAccessFile tmpRAF2 = new RandomAccessFile(tmpFile2, "rw");
        tmpRAF1.setLength(4096);
        tmpRAF1.write(content1);
        tmpRAF2.setLength(4096);
        tmpRAF2.write(content2);
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        dufs.appendFile("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", tmpFile1);
        dufs.appendFile("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", tmpFile2);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] contentRead = new byte[4096];
        volume.read(contentRead);
        assertArrayEquals(contentRead, content1);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 2));
        volume.read(contentRead);
        assertArrayEquals(contentRead, content2);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        assertEquals(2, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(3, volume.readInt());
        assertEquals(1, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(2, volume.readInt());
        tmpRAF1.close();
        tmpFile1.delete();
        tmpRAF2.close();
        tmpFile2.delete();
    }

    @Test
    void readFile_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        File tmpFile = new File("tmp.txt");
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class,
                        () -> nullVolumeDufs.readFile("vol.DUFS", tmpFile)).getMessage());
    }

    @Test
    void readFile_nullFile() {
        File tmp = new File("tmp");
        assertEquals("Given path does not exist.",
                assertThrows(DufsException.class,
                        () -> dufs.readFile("vol.DUFS"
                                + FileSystems.getDefault().getSeparator() + "record", tmp)).getMessage());
    }

    @Test
    void readFile() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        byte[] content = new byte[1024];
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        // fill content with some random values
        for (int i = 0; i < 1024; ++i) {
            content[i] = (byte) ((i + 44) ^ 57);
        }
        volume.write(content);
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1) + RecordOffsets.SIZE_OFFSET);
        volume.writeLong(1024);
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        dufs.readFile("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", tmpFile);
        byte[] readContent = new byte[1024];
        tmpRAF.seek(0);
        tmpRAF.read(readContent);
        assertArrayEquals(content, readContent);
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void readFile_moreThanOneCluster() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        byte[] content = new byte[8192];
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        // fill content with some random values
        for (int i = 0; i < 8192; ++i) {
            content[i] = (byte) ((i + 44) ^ 57);
        }
        volume.write(content);
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1) + RecordOffsets.SIZE_OFFSET);
        volume.writeLong(8192);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        volume.writeInt(2);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(3);
        volume.writeInt(1);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(2);
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        dufs.readFile("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", tmpFile);
        byte[] readContent = new byte[8192];
        tmpRAF.seek(0);
        tmpRAF.read(readContent);
        assertArrayEquals(content, readContent);
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void deleteRecord_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class,
                        () -> nullVolumeDufs.deleteRecord("vol.DUFS", Mockito.anyByte())).getMessage());
    }

    @Test
    void deleteRecord_directoryNotEmpty() throws IOException, DufsException {
        dufs.createRecord("vol.DUFS", "folder", (byte) 0);
        String pathToDirectory = "vol.DUFS" + FileSystems.getDefault().getSeparator() + "folder";
        dufs.createRecord(pathToDirectory, "file", (byte) 1);
        assertEquals("Directory is not empty",
                assertThrows(DufsException.class,
                        () -> dufs.deleteRecord(pathToDirectory, (byte) 0)).getMessage());
    }

    @Test
    void deleteRecord_nullFile() {
        assertEquals("Given path does not exist.",
                assertThrows(DufsException.class,
                        () -> dufs.deleteRecord("vol.DUFS"
                                + FileSystems.getDefault().getSeparator() + "record", Mockito.anyByte())).getMessage());
    }

    @Test
    void deleteRecord() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        dufs.deleteRecord("vol.DUFS"
                + FileSystems.getDefault().getSeparator() + "record", (byte) 1);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        assertEquals(0, volume.readInt());
        assertEquals(0, volume.readInt());
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1));
        byte[] recordBytes = new byte[RecordListOffsets.RECORD_SIZE];
        byte[] emptyRecordBytes = new byte[RecordListOffsets.RECORD_SIZE];
        volume.read(recordBytes);
        assertArrayEquals(emptyRecordBytes, recordBytes);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] clusterBytes = new byte[RecordListOffsets.RECORD_SIZE];
        byte[] emptyClusterBytes = new byte[RecordListOffsets.RECORD_SIZE];
        volume.read(clusterBytes);
        assertArrayEquals(emptyClusterBytes, clusterBytes);
        // check if record is deleted from parent directory's cluster
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        assertEquals(0, volume.readInt());
        assertEquals(0, volume.readInt());
    }

    @Test
    void renameRecord_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class,
                        () -> nullVolumeDufs.renameRecord("vol.DUFS", Mockito.anyString(),
                                Mockito.anyByte())).getMessage());
    }

    @Test
    void renameRecord_nameLength() throws IOException, DufsException {
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        assertEquals("New name length has exceeded the limit.",
                assertThrows(DufsException.class,
                        () -> dufs.renameRecord("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record",
                                "123456789123456789123456789123456", (byte) 0)).getMessage());
    }

    @Test
    void renameRecord_nameProhibitedSymbols() throws IOException, DufsException {
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        assertEquals("New name contains prohibited symbols.",
                assertThrows(DufsException.class,
                        () -> dufs.renameRecord("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record",
                                "123*", (byte) 1)).getMessage());
    }

    @Test
    void renameRecord_duplicate() throws IOException, DufsException {
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        dufs.createRecord("vol.DUFS", "doppelganger", (byte) 1);
        assertEquals("Record with such name and type already contains in this path.",
                assertThrows(DufsException.class,
                        () -> dufs.renameRecord("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record",
                                "doppelganger", (byte) 1)).getMessage());
    }

    @Test
    void renameRecord_nullRecord() {
        assertEquals("Given path does not exist.",
                assertThrows(DufsException.class,
                        () -> dufs.createRecord("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record",
                                "somename", (byte) 0)).getMessage());
    }

    @Test
    void renameRecord() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        dufs.renameRecord("vol.DUFS" + FileSystems.getDefault().getSeparator()
                + "record", "file", (byte) 1);
        Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, 1);
        assertEquals(new String(Arrays.copyOf("file".toCharArray(), 32)), new String(record.getName()));
    }

    @Test
    void moveRecord_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class,
                        () -> nullVolumeDufs.moveRecord("vol.DUFS", Mockito.anyString(),
                                Mockito.anyByte())).getMessage());
    }

    @Test
    void moveRecord_nullRecord() {
        assertEquals("Given path does not exist.",
                assertThrows(DufsException.class,
                        () -> dufs.createRecord("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record",
                                Mockito.anyString(), (byte) 0)).getMessage());
    }

    @Test
    void moveRecord() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "folder", (byte) 0);
        dufs.createRecord("vol.DUFS" + FileSystems.getDefault().getSeparator() + "folder",
                "file", (byte) 1);
        dufs.moveRecord("vol.DUFS" + FileSystems.getDefault().getSeparator() + "folder"
                + FileSystems.getDefault().getSeparator() + "file", "vol.DUFS", (byte) 1);
        Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, 2);
        assertEquals(0, record.getParentDirectoryIndex());
        assertEquals(2, record.getParentDirectoryIndexOrderNumber());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        assertEquals(0, volume.readInt());
        assertEquals(0, volume.readInt());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        assertEquals(2, volume.readInt());
        assertEquals(1, volume.readInt());
        assertEquals(2, volume.readInt());
    }

    @Test
    void defragmentation_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class, nullVolumeDufs::defragmentation).getMessage());
    }

    @Test
    void defragmentation() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "record1", (byte) 1);
        dufs.createRecord("vol.DUFS", "record2", (byte) 1);
        dufs.createRecord("vol.DUFS", "record3", (byte) 1);
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        tmpRAF.setLength(5000);
        byte[] content2 = new byte[5000];
        for (int i = 0; i < 5000; ++i) {
            content2[i] = (byte) (((i * i) ^ 22) << 3);
        }
        tmpRAF.write(content2);
        dufs.writeFile("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record2", tmpFile);
        tmpRAF.setLength(8300);
        byte[] content3 = new byte[8300];
        for (int i = 0; i < 8300; ++i) {
            content3[i] = (byte) ((i * i) ^ 505 - i);
        }
        tmpRAF.seek(0);
        tmpRAF.write(content3);
        byte[] content1 = new byte[8300];
        dufs.writeFile("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record3", tmpFile);
        for (int i = 0; i < 8300; ++i) {
            content1[i] = (byte) ((i * i) ^ 712 - i);
        }
        tmpRAF.seek(0);
        tmpRAF.write(content1);
        dufs.writeFile("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record1", tmpFile);
        dufs.defragmentation();

        // check cluster chains
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(2, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(3, volume.readInt());
        assertEquals(1, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(2, volume.readInt());

        assertEquals(5, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(4, volume.readInt());

        assertEquals(7, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(8, volume.readInt());
        assertEquals(6, volume.readInt());
        assertEquals(0xFFFFFFFF, volume.readInt());
        assertEquals(7, volume.readInt());
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void bake_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class, nullVolumeDufs::bake).getMessage());
    }

    @Test
    void bake() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        tmpRAF.setLength(1024);
        byte[] content = new byte[1024];
        for (int i = 0; i < 1024; ++i) {
            content[i] = (byte) (((i * i) ^ 22) << 3);
        }
        tmpRAF.write(content);
        dufs.writeFile("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record", tmpFile);
        dufs.bake();
        assertEquals(1018252, volume.length());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        byte[] readContent = new byte[1024];
        volume.read(readContent);
        assertArrayEquals(content, readContent);
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void unbake_nullVolume() {
        Dufs nullVolumeDufs = new Dufs();
        assertEquals("Volume has not found.",
                assertThrows(DufsException.class, nullVolumeDufs::unbake).getMessage());
    }

    @Test
    void unbake() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord("vol.DUFS", "record", (byte) 1);
        File tmpFile = new File("tmp");
        RandomAccessFile tmpRAF = new RandomAccessFile(tmpFile, "rw");
        tmpRAF.setLength(1024);
        byte[] content = new byte[1024];
        for (int i = 0; i < 1024; ++i) {
            content[i] = (byte) (((i * i) ^ 22) << 3);
        }
        tmpRAF.write(content);
        dufs.writeFile("vol.DUFS" + FileSystems.getDefault().getSeparator() + "record", tmpFile);
        dufs.bake();
        dufs.unbake();
        assertEquals(41970060, volume.length());
        tmpRAF.close();
        tmpFile.delete();
    }

    @Test
    void dufsCompleteTest() throws IOException, DufsException {
        String separator = FileSystems.getDefault().getSeparator();
        final byte FILE = 1;
        final byte DIR = 0;
        dufs.createRecord("vol.DUFS", "src", DIR);
        dufs.createRecord("vol.DUFS" + separator + "src", "main", DIR);
        dufs.createRecord("vol.DUFS" + separator + "src" + separator + "main", "java", DIR);
        dufs.createRecord("vol.DUFS" + separator + "src" + separator + "main" + separator
                + "java", "com", DIR);
        dufs.createRecord("vol.DUFS" + separator + "src" + separator + "main" + separator
                + "java" + separator + "com", "dufs", DIR);
        String comdufsPath = "vol.DUFS" + separator + "src" + separator + "main" + separator
                + "java" + separator + "com" + separator + "dufs";
        String comdufsPathReal = "src" + separator + "main" + separator + "java" + separator + "com" + separator + "dufs";
        dufs.createRecord(comdufsPath, "exceptions", DIR);
        dufs.createRecord(comdufsPath, "filesystem", DIR);
        dufs.createRecord(comdufsPath, "model", DIR);
        dufs.createRecord(comdufsPath, "offsets", DIR);
        dufs.createRecord(comdufsPath, "utility", DIR);
        dufs.createRecord(comdufsPath, "Main.java", FILE);
        dufs.writeFile(comdufsPath + separator + "Main.java", new File(comdufsPathReal + separator + "Main.java"));
        String exceptionsPath = comdufsPath + separator + "exceptions";
        dufs.createRecord(exceptionsPath, "DufsException.java", FILE);
        dufs.writeFile(comdufsPath + separator + "exceptions" + separator + "DufsException.java",
                new File(comdufsPathReal + separator + "exceptions" + separator + "DufsException.java"));
        String filesystemPath = "vol.DUFS" + separator + "src" + separator + "main" + separator
                + "java" + separator + "com" + separator + "dufs" + separator + "filesystem";
        dufs.createRecord(filesystemPath, "Dufs.java", FILE);
        dufs.writeFile(comdufsPath + separator + "filesystem" + separator + "Dufs.java",
                new File(comdufsPathReal + separator + "filesystem" + separator + "Dufs.java"));
        String modelPath = comdufsPath + separator + "model";
        dufs.createRecord(modelPath, "ClusterIndexElement.java", FILE);
        dufs.writeFile(comdufsPath + separator + "model" + separator + "ClusterIndexElement.java",
                new File(comdufsPathReal + separator + "model" + separator + "ClusterIndexElement.java"));
        dufs.createRecord(modelPath, "ClusterIndexList.java", FILE);
        dufs.writeFile(comdufsPath + separator + "model" + separator + "ClusterIndexList.java",
                new File(comdufsPathReal + separator + "model" + separator + "ClusterIndexList.java"));
        dufs.createRecord(modelPath, "Record.java", FILE);
        dufs.writeFile(comdufsPath + separator + "model" + separator + "Record.java",
                new File(comdufsPathReal + separator + "model" + separator + "Record.java"));
        dufs.createRecord(modelPath, "RecordList.java", FILE);
        dufs.writeFile(comdufsPath + separator + "model" + separator + "RecordList.java",
                new File(comdufsPathReal + separator + "model" + separator + "RecordList.java"));
        dufs.createRecord(modelPath, "ReservedSpace.java", FILE);
        dufs.writeFile(comdufsPath + separator + "model" + separator + "ReservedSpace.java",
                new File(comdufsPathReal + separator + "model" + separator + "ReservedSpace.java"));
        String offsetsPath = comdufsPath + separator + "offsets";
        dufs.createRecord(offsetsPath, "ClusterIndexListOffsets.java", FILE);
        dufs.writeFile(comdufsPath + separator + "offsets" + separator + "ClusterIndexListOffsets.java",
                new File(comdufsPathReal + separator + "offsets" + separator + "ClusterIndexListOffsets.java"));
        dufs.createRecord(offsetsPath, "RecordListOffsets.java", FILE);
        dufs.writeFile(comdufsPath + separator + "offsets" + separator + "RecordListOffsets.java",
                new File(comdufsPathReal + separator + "offsets" + separator + "RecordListOffsets.java"));
        dufs.createRecord(offsetsPath, "RecordOffsets.java", FILE);
        dufs.writeFile(comdufsPath + separator + "offsets" + separator + "RecordOffsets.java",
                new File(comdufsPathReal + separator + "offsets" + separator + "RecordOffsets.java"));
        dufs.createRecord(offsetsPath, "ReservedSpaceOffsets.java", FILE);
        dufs.writeFile(comdufsPath + separator + "offsets" + separator + "ReservedSpaceOffsets.java",
                new File(comdufsPathReal + separator + "offsets" + separator + "ReservedSpaceOffsets.java"));
        String utilityPath = comdufsPath + separator + "utility";
        dufs.createRecord(utilityPath, "DateUtility.java", FILE);
        dufs.writeFile(comdufsPath + separator + "utility" + separator + "DateUtility.java",
                new File(comdufsPathReal + separator + "utility" + separator + "DateUtility.java"));
        dufs.createRecord(utilityPath, "Parser.java", FILE);
        dufs.writeFile(comdufsPath + separator + "utility" + separator + "Parser.java",
                new File(comdufsPathReal + separator + "utility" + separator + "Parser.java"));
        dufs.createRecord(utilityPath, "PrintUtility.java", FILE);
        dufs.writeFile(comdufsPath + separator + "utility" + separator + "PrintUtility.java",
                new File(comdufsPathReal + separator + "utility" + separator + "PrintUtility.java"));
        dufs.createRecord(utilityPath, "VolumeHelper.java", FILE);
        dufs.writeFile(comdufsPath + separator + "utility" + separator + "VolumeHelper.java",
                new File(comdufsPathReal + separator + "utility" + separator + "VolumeHelper.java"));
        dufs.createRecord(utilityPath, "VolumeIO.java", FILE);
        dufs.writeFile(comdufsPath + separator + "utility" + separator + "VolumeIO.java",
                new File(comdufsPathReal + separator + "utility" + separator + "VolumeIO.java"));
        dufs.createRecord(utilityPath, "VolumePointerUtility.java", FILE);
        dufs.writeFile(comdufsPath + separator + "utility" + separator + "VolumePointerUtility.java",
                new File(comdufsPathReal + separator + "utility" + separator + "VolumePointerUtility.java"));
        dufs.createRecord(utilityPath, "VolumeUtility.java", FILE);
        dufs.writeFile(comdufsPath + separator + "utility" + separator + "VolumeUtility.java",
                new File(comdufsPathReal + separator + "utility" + separator + "VolumeUtility.java"));
        dufs.deleteRecord(comdufsPath + separator + "Main.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "exceptions" + separator + "DufsException.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "filesystem" + separator + "Dufs.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "model" + separator + "Record.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "model" + separator + "ClusterIndexList.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "model" + separator + "ReservedSpace.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "offsets" + separator + "ClusterIndexListOffsets.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "offsets" + separator + "RecordListOffsets.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "offsets" + separator + "ReservedSpaceOffsets.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "utility" + separator + "VolumeIO.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "utility" + separator + "VolumePointerUtility.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "utility" + separator + "Parser.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "utility" + separator + "PrintUtility.java", FILE);
        dufs.deleteRecord(comdufsPath + separator + "utility" + separator + "VolumeHelper.java", FILE);

        dufs.defragmentation();

        dufs.createRecord(comdufsPath, "returnedfiles1", DIR);
        dufs.createRecord(comdufsPath + separator + "returnedfiles1", "DufsException.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles1" + separator + "DufsException.java",
                new File(comdufsPathReal + separator + "exceptions" + separator + "DufsException.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles1", "Dufs.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles1" + separator + "Dufs.java",
                new File(comdufsPathReal + separator + "filesystem" + separator + "Dufs.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles1", "Record.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles1" + separator + "Record.java",
                new File(comdufsPathReal + separator + "model" + separator + "Record.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles1", "ClusterIndexList.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles1" + separator + "ClusterIndexList.java",
                new File(comdufsPathReal + separator + "model" + separator + "ClusterIndexList.java"));
        dufs.createRecord(comdufsPath, "returnedfiles2", DIR);
        dufs.createRecord(comdufsPath + separator + "returnedfiles2", "ReservedSpace.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles2" + separator + "ReservedSpace.java",
                new File(comdufsPathReal + separator + "model" + separator + "ReservedSpace.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles2", "ClusterIndexListOffsets.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles2" + separator + "ClusterIndexListOffsets.java",
                new File(comdufsPathReal + separator + "offsets" + separator + "ClusterIndexListOffsets.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles2", "RecordListOffsets.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles2" + separator + "RecordListOffsets.java",
                new File(comdufsPathReal + separator + "offsets" + separator + "RecordListOffsets.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles2", "ReservedSpaceOffsets.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles2" + separator + "ReservedSpaceOffsets.java",
                new File(comdufsPathReal + separator + "offsets" + separator + "ReservedSpaceOffsets.java"));
        dufs.createRecord(comdufsPath, "returnedfiles3", DIR);
        dufs.createRecord(comdufsPath + separator + "returnedfiles3", "VolumeIO.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles3" + separator + "VolumeIO.java",
                new File(comdufsPathReal + separator + "utility" + separator + "VolumeIO.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles3", "VolumePointerUtility.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles3" + separator + "VolumePointerUtility.java",
                new File(comdufsPathReal + separator + "utility" + separator + "VolumePointerUtility.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles3", "Parser.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles3" + separator + "Parser.java",
                new File(comdufsPathReal + separator + "utility" + separator + "Parser.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles3", "PrintUtility.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles3" + separator + "PrintUtility.java",
                new File(comdufsPathReal + separator + "utility" + separator + "PrintUtility.java"));
        dufs.createRecord(comdufsPath + separator + "returnedfiles3", "VolumeHelper.java", FILE);
        dufs.writeFile(comdufsPath + separator + "returnedfiles3" + separator + "VolumeHelper.java",
                new File(comdufsPathReal + separator + "utility" + separator + "VolumeHelper.java"));

        dufs.bake();
        dufs.closeVolume();
        dufs.attachVolume("vol.DUFS");
        dufs.printVolumeRecords();
        dufs.printDirectoryTree();
    }
}