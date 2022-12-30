package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;
import com.dufs.model.ReservedSpace;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class VolumeHelperTest {

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
    void deleteFile() throws IOException {
        dufs.closeVolume();
        file.delete();
    }

    @Test
    void howMuchClustersNeeds() {
        int clustersAmount = VolumeHelper.howMuchClustersNeeds(reservedSpace, 4096000);
        assertEquals(1000, clustersAmount);
    }

    @Test
    void howMuchClusterDirectoryTakes() throws IOException {
        int clustersAmount = VolumeHelper.howMuchClustersDirectoryTakes(dufs.getVolume(), reservedSpace, 0);
        assertEquals(1, clustersAmount);
    }

    @Test
    void calculateVolumeSize() {
        long volumeSize = VolumeHelper.calculateVolumeSize(reservedSpace.getClusterSize(), 4096000);
        assertEquals(4201060, volumeSize);
    }

    @Test
    void clustersAmount() {
        int clustersAmount = VolumeHelper.clustersAmount(reservedSpace.getClusterSize(), 4096000);
        assertEquals(1000, clustersAmount);
    }

    @Test
    void enoughSpace_true() {
        long okSize = 4096000 - reservedSpace.getClusterSize();
        assertTrue(VolumeHelper.enoughSpace(reservedSpace, okSize));
    }

    @Test
    void enoughSpace_false() {
        long notOkSize = 4096000 - reservedSpace.getClusterSize() + 1;
        assertFalse(VolumeHelper.enoughSpace(reservedSpace, notOkSize));
    }

    @Test
    void recordExists_byClusterIndex_true() throws IOException {
        assertTrue(VolumeHelper.recordExists(dufs.getVolume(), 0));
    }

    @Test
    void recordExists_byClusterIndex_false() throws IOException {
        assertFalse(VolumeHelper.recordExists(dufs.getVolume(), Mockito.anyInt() + 1)); // not 0th
    }

    @Test
    void recordExists_byRecordIndex_true() throws IOException {
        assertTrue(VolumeHelper.recordExists(dufs.getVolume(), reservedSpace, 0));
    }

    @Test
    void recordExists_byRecordIndex_false() throws IOException {
        assertFalse(VolumeHelper.recordExists(dufs.getVolume(), reservedSpace,Mockito.anyInt() + 1)); // not 0th
    }

    @Test
    void isNameUniqueInDirectory_true() throws IOException, DufsException {
        assertTrue(VolumeHelper.isNameUniqueInDirectory(dufs.getVolume(), reservedSpace, 0,
                "unique".toCharArray(), (byte) 1));
    }

    @Test
    void isNameUniqueInDirectory_false() throws IOException, DufsException {
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "unique", (byte) 1);
        assertFalse(VolumeHelper.isNameUniqueInDirectory(dufs.getVolume(), reservedSpace, 0,
                "unique".toCharArray(), (byte) 1));
    }

    @Test
    void isDirectoryEmpty_true() throws IOException {
        assertTrue(VolumeHelper.isDirectoryEmpty(dufs.getVolume(), reservedSpace, 0));
    }

    @Test
    void isDirectoryEmpty_false() throws IOException, DufsException {
        dufs.createRecord(new String(reservedSpace.getVolumeName()), Mockito.anyString(), (byte) 1);
        assertFalse(VolumeHelper.isDirectoryEmpty(dufs.getVolume(), reservedSpace, 0));
    }
}