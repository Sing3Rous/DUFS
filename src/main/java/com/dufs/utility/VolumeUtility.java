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
import java.nio.ByteBuffer;
import java.util.Arrays;

public class VolumeUtility {
    public static void createClusterIndexChain(RandomAccessFile volume, ReservedSpace reservedSpace, int clusterIndex) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(clusterIndex));
        volume.writeInt(0xFFFFFFFF);    // ClusterIndexElement.nextClusterIndex
        volume.writeInt(0xFFFFFFFF);    // ClusterIndexElement.prevClusterIndex
        int nextClusterIndex = findNextFreeClusterIndex(volume, reservedSpace);
        reservedSpace.setNextClusterIndex(nextClusterIndex);
        volume.seek(ReservedSpaceOffsets.NEXT_CLUSTER_INDEX_OFFSET);
        volume.writeInt(nextClusterIndex);
        volume.seek(defaultFilePointer);
    }

    public static int updateClusterIndexChain(RandomAccessFile volume, ReservedSpace reservedSpace, int clusterIndex) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        int nextClusterIndex = reservedSpace.getNextClusterIndex();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(clusterIndex));
        volume.writeInt(nextClusterIndex);
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(nextClusterIndex));
        volume.writeInt(0xFFFFFFFF);    // write ClusterIndexElement.nextClusterIndex as end of chain
        volume.writeInt(clusterIndex);     // write ClusterIndexElement.prevClusterIndex as index of previous cluster in chain
        reservedSpace.setNextClusterIndex(findNextFreeClusterIndex(volume, reservedSpace));
        volume.seek(defaultFilePointer);
        return nextClusterIndex;
    }

    public static void allocateInCluster(RandomAccessFile volume, ReservedSpace reservedSpace, int clusterIndex,
                                         byte[] content, int pos) throws DufsException, IOException {
        if (content.length > (reservedSpace.getClusterSize() - pos)) {
            throw new DufsException("Given content is bigger than the space left in the cluster.");
        }
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex) + pos);
        volume.write(content);
        if (content.length + pos == reservedSpace.getClusterSize()) {   // if cluster is filled
            reservedSpace.setNextClusterIndex(findNextFreeClusterIndex(volume, reservedSpace));
        }
        volume.seek(defaultFilePointer);
    }

    public static void deleteRecord(RandomAccessFile volume, ReservedSpace reservedSpace,
                                    Record record, int recordIndex) throws IOException, DufsException {
        if (recordIndex == 0) {
            throw new DufsException("Root's record cannot be modified");
        }
        long defaultFilePointer = volume.getFilePointer();
        int firstClusterIndex = record.getFirstClusterIndex();
        volume.seek(VolumePointerUtility.calculateRecordPosition(reservedSpace, recordIndex));
        // delete record from record list
        volume.write(new byte[RecordListOffsets.RECORD_SIZE]);      // set next RECORD_SIZE (93) bytes to 0
        int clusterIndex = firstClusterIndex;
        int prevClusterIndex = clusterIndex;
        // delete record from cluster index list and data in clusters
        do {
            volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex));
            volume.write(new byte[reservedSpace.getClusterSize()]); // set every value in cluster to 0
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(clusterIndex));
            clusterIndex = volume.readInt();
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(prevClusterIndex));
            prevClusterIndex = clusterIndex;
            volume.write(new byte[8]);                              // set every value in ClusterIndexList to 0
        } while (clusterIndex != 0xFFFFFFFF);
        // delete record index from parent directory cluster
        removeRecordIndexFromDirectoryCluster(volume, reservedSpace,
                record.getParentDirectoryIndex(), record.getParentDirectoryIndexOrderNumber());
        volume.seek(defaultFilePointer);
    }

    /*
     * currently it uses linear search, which is bad (!)
     * architecturally it could be remade on b-trees-like data structure and find record by O(logn)
    */
    public static int findDirectoryIndex(RandomAccessFile volume, ReservedSpace reservedSpace, String path) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        String[] records = Parser.parsePath(path);
        // check if root name is the first directory in the given path
        if (records.length == 0 || !Arrays.equals(Arrays.copyOf(records[0].toCharArray(), 8),
                                                    Arrays.copyOf((reservedSpace.getVolumeName()), 8))) {
            throw new DufsException("Given path is not correct.");
        }
        // check if root name is <= 8 symbols
        if (records[0].length() > 8) {
            throw new DufsException("Root name is incorrect.");
        }
        // if there is only root in the path
        if (records.length == 1) {
            return 0;
        }
        int clusterIndex = 0;                                                                   // start traverse from the 0th (root) directory
        long clusterIndexPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex) + 4;  // skip first 4 bytes of root's cluster
        int recordIndex = 0;
        for (int i = 1; i < records.length; ++i) {                                              // iterate over directories in the path
            boolean hasFound = false;
            do {                                                                                // iterate over clusters in the chain
                volume.seek(clusterIndexPosition);
                recordIndex = volume.readInt();
                int counter = 0;
                while (recordIndex != 0 && counter < (reservedSpace.getClusterSize() / 4)) {    // iterate over record indexes in the cluster
                    Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, recordIndex);
                    if (Arrays.equals(Arrays.copyOf(records[i].toCharArray(), 32), record.getName())
                            && record.getIsFile() == 0) {
                        clusterIndex = record.getFirstClusterIndex();
                        clusterIndexPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex) + 4;
                        hasFound = true;
                        break;
                    }
                    recordIndex = volume.readInt();
                    counter++;
                }
                if (hasFound) {
                    break;
                }
                if (recordIndex == 0) {
                    throw new DufsException("Given path does not exist.");
                }
                clusterIndex = findNextClusterIndexInChain(volume, clusterIndex);
                clusterIndexPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex);
            } while (clusterIndex != -1);
        }
        volume.seek(defaultFilePointer);
        return recordIndex;
    }

    /*
     * linear search through the content in directory's cluster chain
     */
    public static int findFileIndex(RandomAccessFile volume, ReservedSpace reservedSpace, String path) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        int directoryIndex = findDirectoryIndex(volume, reservedSpace, Parser.joinPath(Parser.parsePathBeforeFile(path)));
        String fileName = Parser.parseFileNameInPath(path);
        Record directory = VolumeIO.readRecordFromVolume(volume, reservedSpace, directoryIndex);
        int clusterIndex = directory.getFirstClusterIndex();                                // start traverse from the last directory in path
        long clusterIndexPos = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex) + 4;   // skip first 4 bytes of root's cluster
        int recordIndex;
        boolean hasFound = false;
        do {                                                                                // iterate over clusters in the chain
            volume.seek(clusterIndexPos);
            recordIndex = volume.readInt();
            int counter = 0;
            while (recordIndex != 0 && counter < (reservedSpace.getClusterSize() / 4)) {    // iterate over record indexes in the cluster
                Record record = VolumeIO.readRecordFromVolume(volume, reservedSpace, recordIndex);
                if (Arrays.equals(Arrays.copyOf(fileName.toCharArray(), 32), record.getName())
                        && record.getIsFile() == 1) {
                    hasFound = true;
                    break;
                }
                recordIndex = volume.readInt();
                counter++;
            }
            if (hasFound) {
                break;
            }
            if (recordIndex == 0) {
                throw new DufsException("Given path does not exist.");
            }
            clusterIndex = findNextClusterIndexInChain(volume, clusterIndex);
            clusterIndexPos = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex);
        } while (clusterIndex != -1);
        volume.seek(defaultFilePointer);
        return recordIndex;
    }

    /*
     * runs through the cluster indexes (starting from ReservedSpace.nextClusterIndex) and returns index of first met 0;
     */
    public static int findNextFreeClusterIndex(RandomAccessFile volume, ReservedSpace reservedSpace) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        int currentClusterIndex = reservedSpace.getNextClusterIndex();
        long currentClusterIndexPosition = VolumePointerUtility.calculateClusterIndexPosition(currentClusterIndex);
        int nextFreeClusterIndex = currentClusterIndex - 1;
        int clusterIndexElementData;
        final long MAX_CLUSTER_INDEX_POSITION = VolumePointerUtility.calculateClusterIndexPosition(reservedSpace.getReservedClusters());
        do {
            volume.seek(currentClusterIndexPosition);
            nextFreeClusterIndex++;
            clusterIndexElementData = volume.readInt(); // read 4 bytes of ClusterIndexElement.nextClusterIndex
            currentClusterIndexPosition += ClusterIndexListOffsets.CLUSTER_INDEX_ELEMENT_SIZE;  // skip 4 bytes of ClusterIndexElement.prevClusterIndex
            if (currentClusterIndexPosition > MAX_CLUSTER_INDEX_POSITION) {
                nextFreeClusterIndex = 1;   // continue searching from 1st cluster
                currentClusterIndexPosition = VolumePointerUtility.calculateClusterIndexPosition(nextFreeClusterIndex);
            }
        } while (clusterIndexElementData != 0);
        volume.seek(defaultFilePointer);
        return nextFreeClusterIndex;
    }

    public static int findNextFreeRecordIndex(RandomAccessFile volume, ReservedSpace reservedSpace) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        int currentRecordIndex = reservedSpace.getNextRecordIndex();
        long currentRecordPosition = VolumePointerUtility.calculateRecordPosition(reservedSpace, currentRecordIndex);
        short recordCreateDate;
        int nextFreeRecordIndex = currentRecordIndex - 1;
        final long MAX_RECORD_POSITION = VolumePointerUtility.calculateRecordPosition(reservedSpace, reservedSpace.getReservedClusters()); // equal to number of clusters * Record's size in bytes
        do {
            volume.seek(currentRecordPosition + RecordOffsets.CREATE_DATE_OFFSET);
            nextFreeRecordIndex++;
            recordCreateDate = volume.readShort();  // read 2 bytes of Record.createDate, because if it is 0 -- record is empty
            if (currentRecordPosition > MAX_RECORD_POSITION) {  // if traversal should continue from the 1st record
                nextFreeRecordIndex = 1;
                currentRecordPosition = VolumePointerUtility.calculateRecordPosition(reservedSpace, nextFreeRecordIndex);
            }
            currentRecordPosition += RecordListOffsets.RECORD_SIZE; // skip RECORD_SIZE (93) bytes of record till next record's createDate
        } while (recordCreateDate != 0);
        volume.seek(defaultFilePointer);
        return nextFreeRecordIndex;
    }

    /*
     * returns -1 if given cluster is the last in the chain
     */
    public static int findNextClusterIndexInChain(RandomAccessFile volume, int clusterIndex) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(clusterIndex));
        int nextCluster = volume.readInt();
        if (nextCluster == 0) {
            throw new DufsException("Given cluster chain is broken.");
        }
        volume.seek(defaultFilePointer);
        return (nextCluster != 0xFFFFFFFF) ? nextCluster : -1;
    }

    public static int findLastClusterIndexInChain(RandomAccessFile volume, int clusterIndex) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        int prevIndex;
        int index = clusterIndex;
        do {
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(index));
            prevIndex = index;
            index = volume.readInt();
            if (index == 0) {
                throw new DufsException("Given cluster chain is broken.");
            }
        } while (index != 0xFFFFFFFF);
        volume.seek(defaultFilePointer);
        return prevIndex;
    }

    public static int addRecordIndexInDirectoryCluster(RandomAccessFile volume, ReservedSpace reservedSpace,
                                                       int recordIndex, int parentDirectoryClusterIndex) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(VolumePointerUtility.calculateClusterIndexPosition(parentDirectoryClusterIndex));
        int nextClusterIndex = volume.readInt();
        if (nextClusterIndex == 0) {
            throw new DufsException("Parent directory cluster is empty.");
        }
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, parentDirectoryClusterIndex));
        int numberOfRecordsInDirectory = volume.readInt();
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, parentDirectoryClusterIndex));
        volume.writeInt(numberOfRecordsInDirectory + 1);
        int indexInsertionOffset = (int) (((numberOfRecordsInDirectory + 1) * 4L) % reservedSpace.getClusterSize());
        int lastClusterIndex = findLastClusterIndexInChain(volume, parentDirectoryClusterIndex);
        allocateInCluster(volume, reservedSpace, lastClusterIndex,
                ByteBuffer.allocate(4).putInt(recordIndex).array(), indexInsertionOffset);
        volume.seek(defaultFilePointer);
        return numberOfRecordsInDirectory + 1;
    }

    public static void removeRecordIndexFromDirectoryCluster(RandomAccessFile volume, ReservedSpace reservedSpace, int parentDirectoryIndex,
                                                             int parentDirectoryIndexOrderNumber) throws IOException, DufsException {
        long defaultFilePointer = volume.getFilePointer();
        long clusterPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, parentDirectoryIndex);
        volume.seek(clusterPosition);
        int numberOfRecordsInDirectory = volume.readInt();
        if (numberOfRecordsInDirectory == 0) {
            throw new DufsException("Directory is empty.");
        }
        volume.seek(clusterPosition);
        volume.writeInt(numberOfRecordsInDirectory - 1);
        // traverse cluster chain to find neededClusterIndex and lastClusterIndex
        Record directory = VolumeIO.readRecordFromVolume(volume, reservedSpace, parentDirectoryIndex);
        int clusterIndex = directory.getFirstClusterIndex();
        int neededClusterOrderNumber = Math.floorDiv(parentDirectoryIndexOrderNumber * 4, reservedSpace.getClusterSize());
        int clusterOrderNumber = 0;
        long neededClusterIndexPosition = 0;
        do {
            if (clusterOrderNumber == neededClusterOrderNumber) {
                neededClusterIndexPosition = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex) +
                        ((parentDirectoryIndexOrderNumber * 4L) % reservedSpace.getClusterSize());
            }
            clusterOrderNumber++;
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(clusterIndex));
            clusterIndex = volume.readInt();
        } while (clusterIndex != 0xFFFFFFFF);
        int lastClusterIndex = volume.readInt();
        if (lastClusterIndex != 0xFFFFFFFF) {
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(lastClusterIndex));  // read ClusterIndexElement.prevClusterIndex and seek to that cluster index
            lastClusterIndex = volume.readInt();                                                // read index of last cluster in chain
        } else {
            lastClusterIndex = parentDirectoryIndex;
        }
        int lastRecordPositionOffset = (int) ((numberOfRecordsInDirectory * 4L) % reservedSpace.getClusterSize());
        volume.seek(VolumePointerUtility.calculateClusterPosition(reservedSpace, lastClusterIndex) + lastRecordPositionOffset);
        long lastRecordPosition = volume.getFilePointer();
        volume.seek(neededClusterIndexPosition);
        volume.writeInt(0);
        swapIndexesInDirectoryCluster(volume, neededClusterIndexPosition, lastRecordPosition);
        // if last cluster becomes empty
        if (numberOfRecordsInDirectory % (reservedSpace.getClusterSize() / 4) == 0) {
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(lastClusterIndex));
            volume.writeInt(0);
            int prevClusterIndex = volume.readInt();
            volume.seek(VolumePointerUtility.calculateClusterIndexPosition(prevClusterIndex));
            volume.writeInt(0xFFFFFFFF);
        }
        volume.seek(defaultFilePointer);
    }

    // unsafe: doesn't check anything
    public static void swapIndexesInDirectoryCluster(RandomAccessFile volume, long pos1, long pos2) throws  IOException {
        long defaultFilePointer = volume.getFilePointer();
        volume.seek(pos1);
        int recordIndex1 = volume.readInt();
        volume.seek(pos2);
        int recordIndex2 = volume.readInt();
        volume.seek(pos2);
        volume.writeInt(recordIndex1);
        volume.seek(pos1);
        volume.writeInt(recordIndex2);
        volume.seek(defaultFilePointer);
    }

    // unsafe: doesn't check anything
    public static void swapClusters(RandomAccessFile volume, ReservedSpace reservedSpace, int clusterIndex1, int clusterIndex2) throws IOException {
        long defaultFilePointer = volume.getFilePointer();
        // swap clusters
        long clusterPos1 = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex1);
        long clusterPos2 = VolumePointerUtility.calculateClusterPosition(reservedSpace, clusterIndex2);
        volume.seek(clusterPos1);
        byte[] cluster1 = new byte[reservedSpace.getClusterSize()];
        volume.read(cluster1);
        volume.seek(clusterPos2);
        byte[] cluster2 = new byte[reservedSpace.getClusterSize()];
        volume.read(cluster2);
        volume.seek(clusterPos2);
        volume.write(cluster1);
        volume.seek(clusterPos1);
        volume.write(cluster2);
        volume.seek(defaultFilePointer);
    }
}