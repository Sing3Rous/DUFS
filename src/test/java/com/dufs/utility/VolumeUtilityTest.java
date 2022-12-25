package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;
import com.dufs.model.Record;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.ReservedSpaceOffsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VolumeUtilityTest {

    private static Dufs dufs;
    private static ReservedSpace reservedSpace;
    private static File file;

    @BeforeAll
    static void init() throws IOException, DufsException {
        file = new File("vol.DUFS");
        dufs = new Dufs();
        dufs.mountVolume(file.getName(), 4096, 4096000);
        reservedSpace = VolumeIO.readReservedSpaceFromVolume(dufs.getVolume());
    }

    @AfterAll
    static void deleteFile() throws IOException {
        dufs.closeVolume();
        file.delete();
    }

    @Test
    void createClusterIndexChain() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        VolumeUtility.createClusterIndexChain(volume, reservedSpace, 303);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(303));
        assertEquals(0xFFFFFFFF, volume.readInt()); // ClusterIndexElement.nextClusterIndex
        assertEquals(0xFFFFFFFF, volume.readInt()); // ClusterIndexElement.prevClusterIndex
    }

    @Test
    void updateClusterIndexChain() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        VolumeUtility.updateClusterIndexChain(volume, reservedSpace, 0);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        assertEquals(1, volume.readInt());          // next cluster index in chain for 0 is 1
        assertEquals(0xFFFFFFFF, volume.readInt()); // 0 is first cluster index in chain
        assertEquals(0xFFFFFFFF, volume.readInt()); // 1 is last cluster index in chain
        assertEquals(0, volume.readInt());          // prev cluster index in chain for 1 is 0
    }

    @Test
    void allocateInCluster_notEnoughSpaceInCluster() {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content = new byte[2048];
        assertEquals("Given content is bigger than the space left in the cluster.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.allocateInCluster(volume, reservedSpace, 0, content, 2049)).getMessage());
    }

    @Test
    void allocateInCluster_filledCluster() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content = new byte[2048];
        content[29] = 4;
        content[30] = 3;
        content[3] = 2;
        content[13] = 1;
        VolumeUtility.allocateInCluster(volume, reservedSpace, 1, content, 0);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 29);
        assertEquals(4, volume.readByte());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 30);
        assertEquals(3, volume.readByte());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 3);
        assertEquals(2, volume.readByte());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 13);
        assertEquals(1, volume.readByte());
        // even allocation was performed in 1st cluster, indexing was not updated, so next cluster is still 1
        volume.seek(ReservedSpaceOffsets.NEXT_CLUSTER_INDEX_OFFSET);
        assertEquals(1, volume.readInt());
    }

    @Test
    void allocateInCluster() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        byte[] content = new byte[2047];
        content[29] = 4;
        content[30] = 3;
        content[3] = 2;
        content[13] = 1;
        VolumeUtility.allocateInCluster(volume, reservedSpace, 1, content, 0);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 29);
        assertEquals(4, volume.readByte());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 30);
        assertEquals(3, volume.readByte());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 3);
        assertEquals(2, volume.readByte());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 13);
        assertEquals(1, volume.readByte());
    }

    @Test
    void deleteRecord_rootDeletion() {
        assertEquals("Root's record cannot be modified",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.deleteRecord(dufs.getVolume(), reservedSpace, new Record(), 0)).getMessage());
    }
    @Test
    void deleteRecord() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "file1", (byte) 1);
        Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, (byte) 1);
        // crutch to write something directly into record's cluster
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 1108);
        volume.writeInt(9691);
        VolumeUtility.deleteRecord(volume, reservedSpace, record, 1);
        byte[] clusterContent = new byte[reservedSpace.getClusterSize()];
        VolumeIO.readClusterFromVolume(volume, reservedSpace, 1, clusterContent);
        // check if cluster is filled only zeros
        assertTrue(IntStream.range(0, clusterContent.length).parallel().allMatch(i -> clusterContent[i] == 0));
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        // check if cluster index chain is empty
        assertEquals(0, volume.readInt());  // ClusterIndexElement.nextClusterIndex
        assertEquals(0, volume.readInt());  // ClusterIndexElement.prevClusterIndex
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        // check if parent's cluster doesn't contain indexes of deleted record
        assertEquals(0, volume.readInt());  // amount of records in parent directory
        assertEquals(0, volume.readInt());  // record index of first record in parent directory
    }

    @Test
    void findDirectoryIndex_incorrectPath_empty() {

    }

    @Test
    void findDirectoryIndex() {

    }

    @Test
    void findDirectoryIndex_moreThanOneClusterInChain() {

    }

    @Test
    void findFileIndex() {
    }

    @Test
    void findNextFreeClusterIndex() {
    }

    @Test
    void findNextFreeRecordIndex() {
    }

    @Test
    void findNextClusterIndexInChain() {
    }

    @Test
    void findLastClusterIndexInChain() {
    }

    @Test
    void addRecordIndexInDirectoryCluster() {
    }

    @Test
    void removeRecordIndexFromDirectoryCluster() {
    }

    @Test
    void swapIndexesInDirectoryCluster() {
    }

    @Test
    void swapClusters() {
    }
}