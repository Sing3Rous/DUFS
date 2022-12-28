package com.dufs.utility;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;
import com.dufs.model.Record;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.ReservedSpaceOffsets;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VolumeUtilityTest {

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
    void findDirectoryIndex_incorrectRootName() {
        assertEquals("Root name is incorrect.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.findDirectoryIndex(dufs.getVolume(), reservedSpace,
                                "vol.DUFSabc")).getMessage());
    }

    @Test
    void findDirectoryIndex_incorrectPathEmpty() {
        assertEquals("Given path is not correct.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.findDirectoryIndex(dufs.getVolume(), reservedSpace,
                                "v0l.DUFS")).getMessage());
    }

    @Test
    void findDirectoryIndex_wrongPath() {
        assertEquals("Given path does not exist.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.findDirectoryIndex(dufs.getVolume(), reservedSpace,
                                "vol.DUFS" + FileSystems.getDefault().getSeparator() + "folder")).getMessage());
    }

    @Test
    void findDirectoryIndex() throws IOException, DufsException {
        int directoryIndex = VolumeUtility.findDirectoryIndex(dufs.getVolume(), reservedSpace,
                new String(reservedSpace.getVolumeName()));
        assertEquals(0, directoryIndex);
    }

    @Test
    void findDirectoryIndex_moreThanOneClusterInChain() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        // crutch to add directory in root's directory
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        volume.writeInt(1);
        volume.writeInt(1);
        Record directory = new Record(Arrays.copyOf("folder".toCharArray(), 32), 1, 0, 0, (byte) 0);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 1, directory);
        // crutch to create 1200 records in "folder". one cluster contains 1024 indexes of record,
        // so this operation "allocates" 1 new cluster
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        volume.writeInt(1200);
        for (int i = 2; i < 1202; ++i) {
            volume.writeInt(i);
            char[] recordName = Arrays.copyOf(("record" + i).toCharArray(), 32);
            Record record = new Record(recordName, i, 1, i - 2, (byte) 0);
            VolumeIO.writeRecordToVolume(volume, reservedSpace, i, record);
        }
        // crutch to update cluster chain of "folder" chain
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        volume.writeInt(2);
        volume.skipBytes(4);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(1);
        String directoryPath = new String(reservedSpace.getVolumeName())
                + FileSystems.getDefault().getSeparator() + "folder" + FileSystems.getDefault().getSeparator();
        int directoryIndexCluster1 = VolumeUtility.findDirectoryIndex(volume, reservedSpace, directoryPath + "record2");
        assertEquals(2, directoryIndexCluster1);
        int directoryIndexCluster2 = VolumeUtility.findDirectoryIndex(volume, reservedSpace, directoryPath + "record1100");
        assertEquals(1100, directoryIndexCluster2);
    }

    @Test
    void findFileIndex_wrongPath() {
        assertEquals("Given path does not exist.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.findFileIndex(dufs.getVolume(), reservedSpace,
                                "vol.DUFS" + FileSystems.getDefault().getSeparator() + "file")).getMessage());
    }


    @Test
    void findFileIndex() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        String rootPath = new String(reservedSpace.getVolumeName());
        dufs.createRecord(rootPath, "file", (byte) 1);
        int fileIndex = VolumeUtility.findFileIndex(volume, reservedSpace,
                rootPath + FileSystems.getDefault().getSeparator() + "file");
        assertEquals(1, fileIndex);
    }

    @Test
    void findFileIndex_moreThanOneClusterInChain() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        // crutch to add directory in root's directory
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        volume.writeInt(1);
        volume.writeInt(1);
        Record directory = new Record(Arrays.copyOf("folder".toCharArray(), 32), 1, 0, 0, (byte) 0);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 1, directory);
        // crutch to create 1200 records in "folder". one cluster contains 1024 indexes of record,
        // so this operation "allocates" 1 new cluster
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        volume.writeInt(1200);
        for (int i = 2; i < 1202; ++i) {
            volume.writeInt(i);
            char[] recordName = Arrays.copyOf(("record" + i).toCharArray(), 32);
            Record record = new Record(recordName, i, 1, i - 2, (byte) 1);
            VolumeIO.writeRecordToVolume(volume, reservedSpace, i, record);
        }
        // crutch to update cluster chain of "folder" chain
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        volume.writeInt(2);
        volume.skipBytes(4);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(1);
        String directoryPath = new String(reservedSpace.getVolumeName())
                + FileSystems.getDefault().getSeparator() + "folder" + FileSystems.getDefault().getSeparator();
        int fileIndex1 = VolumeUtility.findFileIndex(volume, reservedSpace, directoryPath + "record2");
        assertEquals(2, fileIndex1);
        int fileIndex2 = VolumeUtility.findFileIndex(volume, reservedSpace, directoryPath + "record1100");
        assertEquals(1100, fileIndex2);
    }

    @Test
    void findNextFreeClusterIndex() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(1));
        // overwrite 3 clusters
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        int nextFreeCluster = VolumeUtility.findNextFreeClusterIndex(volume, reservedSpace);
        assertEquals(4, nextFreeCluster);
    }

    @Test
    void findNextFreeClusterIndex_rightBounds() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        VolumeIO.updateVolumeNextClusterIndex(volume, 997);
        // overwrite 3 cluster indexes
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(997));
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        int nextFreeCluster = VolumeUtility.findNextFreeClusterIndex(volume, reservedSpace);
        assertEquals(1, nextFreeCluster);
    }

    @Test
    void findNextFreeRecordIndex() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1));
        // overwrite 3 records
        Record record1 = new Record(Arrays.copyOf("record1".toCharArray(), 32), 0, 0,
                0, (byte) 0);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 1, record1);
        Record record2 = new Record(Arrays.copyOf("record2".toCharArray(), 32), 0, 0,
                0, (byte) 0);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 2, record2);
        Record record3 = new Record(Arrays.copyOf("record3".toCharArray(), 32), 0, 0,
                0, (byte) 0);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 3, record3);
        int nextFreeCluster = VolumeUtility.findNextFreeRecordIndex(volume, reservedSpace);
        assertEquals(4, nextFreeCluster);
    }

    @Test
    void findNextFreeRecordIndex_rightBounds() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, 1));
        VolumeIO.updateVolumeNextRecordIndex(volume, 997);
        // overwrite 3 records
        Record record1 = new Record(Arrays.copyOf("record1".toCharArray(), 32), 0, 0,
                0, (byte) 0);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 997, record1);
        Record record2 = new Record(Arrays.copyOf("record2".toCharArray(), 32), 0, 0,
                0, (byte) 0);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 998, record2);
        Record record3 = new Record(Arrays.copyOf("record3".toCharArray(), 32), 0, 0,
                0, (byte) 0);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 999, record3);
        int nextFreeCluster = VolumeUtility.findNextFreeRecordIndex(volume, reservedSpace);
        assertEquals(1, nextFreeCluster);
    }

    @Test
    void findNextClusterIndexInChain_brokenChain() {
        assertEquals("Given cluster chain is broken.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.findNextClusterIndexInChain(dufs.getVolume(), 1)).getMessage());
    }

    @Test
    void findNextClusterIndexInChain() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        volume.writeInt(5);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(5));
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        int nextClusterIndex = VolumeUtility.findNextClusterIndexInChain(volume, 0);
        assertEquals(5, nextClusterIndex);
    }

    @Test
    void findLastClusterIndexInChain_brokenChain() {
        assertEquals("Given cluster chain is broken.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.findLastClusterIndexInChain(dufs.getVolume(), 1)).getMessage());
    }

    @Test
    void findLastClusterIndexInChain() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        volume.writeInt(5);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(5));
        volume.writeInt(115);
        volume.writeInt(0);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(115));
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(5);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        int nextClusterIndex = VolumeUtility.findLastClusterIndexInChain(volume, 0);
        assertEquals(115, nextClusterIndex);
    }

    @Test
    void findLastClusterIndexInChain_startFromMiddle() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        volume.writeInt(5);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(5));
        volume.writeInt(115);
        volume.writeInt(0);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(115));
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(5);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        int nextClusterIndex = VolumeUtility.findLastClusterIndexInChain(volume, 5);
        assertEquals(115, nextClusterIndex);
    }

    @Test
    void addRecordIndexInDirectoryCluster_emptyParentCluster() {
        assertEquals("Parent directory cluster is empty.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.addRecordIndexInDirectoryCluster(dufs.getVolume(),
                                reservedSpace, 0, 5)).getMessage());
    }

    @Test
    void addRecordIndexInDirectoryCluster() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        Record record = new Record(Arrays.copyOf("record".toCharArray(), 32), 0,
                0, 0, (byte) 1);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, 1, record);
        int numberOfRecordsInDirectory = VolumeUtility.addRecordIndexInDirectoryCluster(volume, reservedSpace,
                1, 0);
        assertEquals(1, numberOfRecordsInDirectory);
        String filePath = new String(reservedSpace.getVolumeName())
                + FileSystems.getDefault().getSeparator()
                + "record";
        int recordIndex = VolumeUtility.findFileIndex(volume, reservedSpace, filePath);
        assertEquals(1, recordIndex);
    }

    @Test
    void removeRecordIndexFromDirectoryCluster_emptyDirectory() {
        assertEquals("Directory is empty.",
                assertThrows(DufsException.class,
                        () -> VolumeUtility.removeRecordIndexFromDirectoryCluster(dufs.getVolume(),
                                reservedSpace, 0, 5)).getMessage());
    }

    @Test
    void removeRecordIndexFromDirectoryCluster() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        String path = new String(reservedSpace.getVolumeName());
        dufs.createRecord(path, "record", (byte) 1);
        VolumeUtility.removeRecordIndexFromDirectoryCluster(volume, reservedSpace, 0, 1);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        assertEquals(0, volume.readInt());  // number of records
        assertEquals(0, volume.readInt());  // record index
    }

    @Test
    void removeRecordIndexFromDirectoryCluster_moreThanOneClusterInChain() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        volume.writeInt(1200);
        for (int i = 2; i < 1202; ++i) {
            volume.writeInt(i);
        }
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        volume.writeInt(1);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0);
        VolumeUtility.removeRecordIndexFromDirectoryCluster(volume, reservedSpace, 0, 1025);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        assertEquals(1199, volume.readInt());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1) + 4);
        assertEquals(1201, volume.readInt());
    }

    @Test
    void removeRecordIndexFromDirectoryCluster_lastClusterBecomesEmpty() throws IOException, DufsException {
        RandomAccessFile volume = dufs.getVolume();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        volume.writeInt(1024);
        for (int i = 1; i < 1025; ++i) {
            volume.writeInt(i);
        }
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        volume.writeInt(1);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0xFFFFFFFF);
        volume.writeInt(0);
        VolumeUtility.removeRecordIndexFromDirectoryCluster(volume, reservedSpace, 0, 1024);
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 0));
        assertEquals(1023, volume.readInt());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, 1));
        assertEquals(0, volume.readInt());
        // check cluster chain
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(0));
        assertEquals(0xFFFFFFFF, volume.readInt()); // 0th cluster next cluster index in chain
        assertEquals(0xFFFFFFFF, volume.readInt()); // 0th cluster prev cluster index in chain
        assertEquals(0, volume.readInt());          // 1st cluster next cluster index in chain
        assertEquals(0, volume.readInt());          // 1st cluster prev cluster index in chain
    }

    @Test
    void swapIndexesInDirectoryCluster() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        long clusterPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, 0);
        volume.seek(clusterPosition);
        volume.writeInt(2);
        volume.seek(clusterPosition + 277);
        volume.writeInt(22);
        volume.seek(clusterPosition + 342);
        volume.writeInt(41);
        VolumeUtility.swapIndexesInDirectoryCluster(volume, clusterPosition + 277, clusterPosition + 342);
        volume.seek(clusterPosition + 277);
        assertEquals(41, volume.readInt());
        volume.seek( clusterPosition + 342);
        assertEquals(22, volume.readInt());
    }

    @Test
    void swapClusters() throws IOException {
        RandomAccessFile volume = dufs.getVolume();
        long clusterPosition1 = VolumePointerUtility.calculateClusterPosition(reservedSpace, 3);
        byte[] cluster1 = new byte[reservedSpace.getClusterSize()];
        for (int i = 0; i < reservedSpace.getClusterSize(); ++i) {
            cluster1[i] = (byte) i;
        }
        volume.seek(clusterPosition1);
        volume.write(cluster1);
        long clusterPosition2 = VolumePointerUtility.calculateClusterPosition(reservedSpace, 13);
        byte[] cluster2 = new byte[reservedSpace.getClusterSize()];
        for (int i = 2930; i < reservedSpace.getClusterSize() + 2930; ++i) {
            cluster2[i - 2930] = (byte) i;
        }
        volume.seek(clusterPosition2);
        volume.write(cluster2);
        VolumeUtility.swapClusters(volume, reservedSpace, 3, 13);
        volume.seek(clusterPosition1);
        for (int i = 0; i < reservedSpace.getClusterSize(); ++i) {
            assertEquals(cluster2[i], volume.readByte());
        }
        volume.seek(clusterPosition2);
        for (int i = 0; i < reservedSpace.getClusterSize(); ++i) {
            assertEquals(cluster1[i], volume.readByte());
        }
    }
}