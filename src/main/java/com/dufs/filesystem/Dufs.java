package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;
import com.dufs.model.ClusterIndexList;
import com.dufs.model.Record;
import com.dufs.model.RecordList;
import com.dufs.model.ReservedSpace;
import com.dufs.utility.Parser;
import com.dufs.utility.VolumeUtility;

import java.io.*;

public class Dufs {
    private SharedData sharedData;
    private RandomAccessFile volume;

    public SharedData getSharedData() {
        return sharedData;
    }

    public RandomAccessFile getVolume() {
        return volume;
    }

    public RandomAccessFile mountVolume(String name, int clusterSize, long volumeSize) throws DufsException, IOException {
        if (name.length() > 8) {
            throw new DufsException("Volume name length has exceeded the limit.");
        }
        if (volumeSize > 1.1e12) {  // 1.1e12 == 1TiB == 1024GiB
            throw new DufsException("Volume size is too big.");
        }
        if (new File("/").getUsableSpace() < volumeSize) {
            throw new DufsException("There is not enough space on disk.");
        }
        volume = new RandomAccessFile(name, "rw");
        volume.setLength(volumeSize /* + */);
        ReservedSpace reservedSpace = new ReservedSpace(name.toCharArray(), clusterSize, volumeSize);
        sharedData = new SharedData(reservedSpace);
        volume.write(reservedSpace.serialize());
        ClusterIndexList clusterIndexList = new ClusterIndexList(clusterSize, volumeSize);
        volume.write(clusterIndexList.serialize());
        RecordList recordList = new RecordList(clusterSize, volumeSize);
        volume.write(recordList.serialize());
        return volume;
    }
    
    public RandomAccessFile attachVolume(String name) throws DufsException, IOException {
        File f = new File(name);
        if (!f.exists() || f.isDirectory()) {
            throw new DufsException("There is no volume with such name in this directory.");
        }
        volume = new RandomAccessFile(name, "rw");
        sharedData.setReservedSpace(VolumeUtility.readReservedSpaceFromVolume(volume));
        if (sharedData.getReservedSpace().getDufsNoseSignature() != 0x44554653
            || sharedData.getReservedSpace().getDufsTailSignature() != 0x4A455442) {
            throw new DufsException("Volume signature does not match.");
        }
        sharedData.setRecordListOffset(VolumeUtility.calculateRecordListOffset(sharedData.getReservedSpace()));
        return volume;
    }

    public void createFile(String path, String name) throws IOException, DufsException {
        if (name.length() > 32) {
            throw new DufsException("File name length has exceeded the limit.");
        }
        if (!Parser.isRecordNameOk(name)) {
            throw new DufsException("File name contains prohibited symbols.");
        }
        ReservedSpace reservedSpace = sharedData.getReservedSpace();
        if (!VolumeUtility.enoughSpace(reservedSpace, 0)) {
            throw new DufsException("Not enough space in the volume to create new file.");
        }
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        int firstClusterIndex = reservedSpace.getNextClusterIndex();
        Record file = new Record(name.toCharArray(), firstClusterIndex, directoryIndex, (byte) 1);
        sharedData.updateNextClusterIndex(VolumeUtility.findNextFreeClusterIndex(volume, reservedSpace));
        VolumeUtility.writeRecordToVolume(volume, reservedSpace, reservedSpace.getNextRecordIndex(), file);
        // TODO: record index should be updated
        VolumeUtility.allocateOneCluster(volume, firstClusterIndex);
    }

    /*
     * writes data from `java.io.File` into clusters in DUFS
     * currently it supports only writing data from external file
     */
    public void writeFile(String path, File file) throws DufsException, IOException {
        ReservedSpace reservedSpace = sharedData.getReservedSpace();
        if (!VolumeUtility.enoughSpace(reservedSpace, file.length())) {
            throw new DufsException("Not enough space in the volume to write this content in file.");
        }
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        byte[] buffer = new byte[reservedSpace.getClusterSize()];
        int clusterIndex = dufsFile.getFirstClusterIndex();
        while (bis.read(buffer) != -1) {
            clusterIndex = VolumeUtility.allocateNewCluster(volume, reservedSpace, clusterIndex, buffer);
            sharedData.updateNextClusterIndex(VolumeUtility.findNextFreeClusterIndex(volume, reservedSpace));   // should it be written here or moved inside allocateNewCluster method
        }
        VolumeUtility.updateRecordSize(volume, reservedSpace, dufsFileIndex, file.length());
    }

    /*
     * appends data to file which already contains some data
     */
    public void appendFile(String path, File file) throws  DufsException, IOException {
        ReservedSpace reservedSpace = sharedData.getReservedSpace();
        if (!VolumeUtility.enoughSpace(reservedSpace, file.length())) {
            throw new DufsException("Not enough space in the volume to write this content in file.");
        }
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        int bytesLeftInCluster = reservedSpace.getClusterSize() - (int) (dufsFile.getSize() % reservedSpace.getClusterSize());
        int firstClusterIndex = dufsFile.getFirstClusterIndex();
        byte[] buffer = new byte[reservedSpace.getClusterSize()];
        int lastClusterIndex = VolumeUtility.lastClusterIndex(volume, firstClusterIndex);
        byte[] lastClusterBuffer = new byte [bytesLeftInCluster];
        int clusterIndex = lastClusterIndex;
        // initially allocate content in the end of last cluster
        if (bis.read(lastClusterBuffer) != -1) {
            VolumeUtility.allocateInExistingCluster(volume, reservedSpace, lastClusterIndex,
                    reservedSpace.getClusterSize() - bytesLeftInCluster, lastClusterBuffer);
        }
        // then allocate content in new clusters
        while (bis.read(buffer) != -1) {
            clusterIndex = VolumeUtility.allocateNewCluster(volume, reservedSpace, clusterIndex, buffer);
            sharedData.updateNextClusterIndex(VolumeUtility.findNextFreeClusterIndex(volume, reservedSpace));   // should it be written here or moved inside allocateNewCluster method
        }
        VolumeUtility.updateRecordSize(volume, reservedSpace, dufsFileIndex, file.length());
    }

    public void readFile(String path, File file) throws IOException, DufsException {
        ReservedSpace reservedSpace = sharedData.getReservedSpace();
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buffer = new byte[reservedSpace.getClusterSize()];
        // read bytes from every cluster in chain but the last
        int clusterIndex = dufsFile.getFirstClusterIndex();
        do {
            VolumeUtility.readClusterFromVolume(volume, reservedSpace, clusterIndex, buffer);
            bos.write(buffer);
        } while ((clusterIndex = VolumeUtility.findNextCluster(volume, clusterIndex)) != -1);
        // read bytes from last cluster in chain
        int bytesLeftInCluster = reservedSpace.getClusterSize() - (int) (dufsFile.getSize() % reservedSpace.getClusterSize());
        byte[] lastClusterBuffer = new byte[bytesLeftInCluster];
        VolumeUtility.readClusterFromVolume(volume, reservedSpace, clusterIndex, lastClusterBuffer);
        bos.write(lastClusterBuffer);
    }

    public void deleteFile(String path) throws DufsException, IOException {
        ReservedSpace reservedSpace = sharedData.getReservedSpace();
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        //
    }

    
    public void renameFile(String path, String newName) {

    }

    
    public void moveFile(String path, String newPath) {

    }

    
    public void createDir(String path, String name) {

    }

    
    public void deleteDir(String path, String name) {

    }

    
    public void renameDir(String path, String oldName, String newName) {

    }

    
    public void moveDir(String oldPath, String name, String newPath) {

    }

    
    public void printDirContent(String path) {

    }

    
    public void defragmentation() {

    }
}
