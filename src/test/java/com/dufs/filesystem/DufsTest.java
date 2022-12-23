package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;
import com.dufs.model.ReservedSpace;
import com.dufs.utility.DateUtility;
import com.dufs.utility.VolumeIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DufsTest {
    private static Dufs dufs;
    private File file;

    @BeforeAll
    static void init() {
        dufs = new Dufs();
    }

    @BeforeEach
    void createFile() throws IOException {
        file = new File("vol.DUFS");
        file.createNewFile();
    }

    @AfterEach
    void deleteFile() {
        file.delete();
    }

    @Test
    void mountVolume_fileExistence() {
        assertEquals("Volume with such name already exists in this directory.",
                assertThrows(DufsException.class, () ->
                dufs.mountVolume(file.getName(), Mockito.anyInt(), Mockito.anyLong())).getMessage());
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
    void mountVolume_fullTest() throws IOException, DufsException {
        final String volumeName = "volume";
        dufs.mountVolume(volumeName, 4096, 4096000);
        File file = new File(volumeName);
        assertTrue(file.exists());
        RandomAccessFile volume = new RandomAccessFile(file, "rw");
        ReservedSpace reservedSpace = VolumeIO.readReservedSpaceFromVolume(volume);
        assertEquals(reservedSpace.getDufsNoseSignature(), 0x44554653);
        assertEquals(reservedSpace.getClusterSize(), 4096);
        assertEquals(reservedSpace.getVolumeSize(), 4197060);
        assertEquals(reservedSpace.getReservedClusters(), 1000);
        assertEquals(reservedSpace.getCreateDate(), DateUtility.dateToShort(LocalDate.now()));
        assertEquals(reservedSpace.getCreateTime(), DateUtility.timeToShort(LocalDateTime.now()));
        assertEquals(reservedSpace.getLastDefragmentationDate(), reservedSpace.getCreateDate());
        assertEquals(reservedSpace.getLastDefragmentationTime(), reservedSpace.getLastDefragmentationTime());
        assertEquals(reservedSpace.getNextClusterIndex(), 1);
        assertEquals(reservedSpace.getFreeClusters(), 999);
        assertEquals(reservedSpace.getNextRecordIndex(), 1);
        assertEquals(reservedSpace.getDufsTailSignature(), 0x4A455442);
        volume.close();
        dufs.closeVolume();
        file.delete();
    }

    @Test
    void attachVolume() {
    }

    @Test
    void createRecord() {
    }

    @Test
    void writeFile() {
    }

    @Test
    void appendFile() {
    }

    @Test
    void readFile() {
    }

    @Test
    void deleteRecord() {
    }

    @Test
    void renameRecord() {
    }

    @Test
    void moveRecord() {
    }

    @Test
    void printDirectoryContent() {
    }

    @Test
    void printVolumeInfo() {
    }

    @Test
    void printVolumeRecords() {
    }

    @Test
    void printDirectoryTree() {
    }

    @Test
    void defragmentation() {
    }

    @Test
    void bake() {
    }

    @Test
    void unbake() {
    }
}