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
    private RandomAccessFile volume;
    private ReservedSpace reservedSpace;

    public RandomAccessFile getVolume() {
        return volume;
    }

    // consider path as argument name
    public void mountVolume(String name, int clusterSize, long volumeSize) throws DufsException, IOException {
        if (new File(name).exists() && !(new File(name).isDirectory())) {
            throw new DufsException("Volume with such name already exists in this directory.");
        }
        if (name.length() > 8) {
            throw new DufsException("Volume name length has exceeded the limit.");
        }
        if (volumeSize > 1.1e12) {  // 1.1e12 == 1TiB == 1024GiB
            throw new DufsException("Volume size is too big.");
        }
        if (new File("/").getUsableSpace() < volumeSize) {
            throw new DufsException("There is not enough space on disk.");
        }
        if (clusterSize % 4 != 0) {
            throw new DufsException("Cluster size cannot be divided by 4 directly.");
        }
        volume = new RandomAccessFile(name, "rw");
        volume.setLength(VolumeUtility.calculateVolumeSize(clusterSize, volumeSize));
        reservedSpace = new ReservedSpace(name.toCharArray(), clusterSize, volumeSize);
        volume.write(reservedSpace.serialize());
        ClusterIndexList clusterIndexList = new ClusterIndexList(clusterSize, volumeSize);
        volume.write(clusterIndexList.serialize());
        VolumeUtility.initializeRootCluster(volume);
        RecordList recordList = new RecordList(clusterSize, volumeSize);
        volume.write(recordList.serialize());
    }

    // consider path as argument name [2]
    public void attachVolume(String name) throws DufsException, IOException {
        File f = new File(name);
        if (!f.exists() || f.isDirectory()) {
            throw new DufsException("There is no volume with such name in this directory.");
        }
        volume = new RandomAccessFile(name, "rw");
        reservedSpace = VolumeUtility.readReservedSpaceFromVolume(volume);
        if (reservedSpace.getDufsNoseSignature() != 0x44554653 || reservedSpace.getDufsTailSignature() != 0x4A455442) {
            throw new DufsException("Volume signature does not match.");
        }
    }

    public void createFile(String path, String name) throws IOException, DufsException {
        if (name.length() > 32) {
            throw new DufsException("File name length has exceeded the limit.");
        }
        if (!Parser.isRecordNameOk(name)) {
            throw new DufsException("File name contains prohibited symbols.");
        }
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        if (!VolumeUtility.isNameUniqueInDirectory(volume, reservedSpace, directoryIndex, name.toCharArray(), (byte) 1)) {
            throw new DufsException("File with such name already contains in this path.");
        }
        if (!VolumeUtility.enoughSpace(reservedSpace, 0)) {
            throw new DufsException("Not enough space in the volume to create new file.");
        }
        int firstClusterIndex = reservedSpace.getNextClusterIndex();
        Record file = new Record(name.toCharArray(), firstClusterIndex, directoryIndex, (byte) 1);
        int recordIndex = reservedSpace.getNextRecordIndex();
        VolumeUtility.writeRecordToVolume(volume, reservedSpace, recordIndex, file);
        reservedSpace.setNextRecordIndex(VolumeUtility.findNextFreeRecordIndex(volume, reservedSpace));
        VolumeUtility.createClusterIndexChain(volume, reservedSpace, firstClusterIndex);
        VolumeUtility.addRecordIndexInDirectoryCluster(volume, reservedSpace, recordIndex, directoryIndex);
    }

    /*
     * writes data from `java.io.File` into clusters in DUFS
     * currently it supports only writing data from external file
     */
    public void writeFile(String path, File file) throws DufsException, IOException {
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
        int bytes;
        while ((bytes = bis.read(buffer)) != -1) {
            if (bytes == reservedSpace.getClusterSize()) {
                VolumeUtility.allocateCluster(volume, reservedSpace, clusterIndex, buffer);
                clusterIndex = VolumeUtility.updateClusterChain(volume, reservedSpace, clusterIndex);
            } else {
                VolumeUtility.allocateCluster(volume, reservedSpace, clusterIndex, buffer);
            }
        }
        VolumeUtility.updateRecordSize(volume, reservedSpace, dufsFileIndex, file.length());
    }

    /*
     * appends data to file which already contains some data
     */
    public void appendFile(String path, File file) throws  DufsException, IOException {
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
        int lastClusterIndex = VolumeUtility.findLastClusterIndex(volume, firstClusterIndex);
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
            reservedSpace.setNextClusterIndex(VolumeUtility.findNextFreeClusterIndex(volume, reservedSpace));   // should it be written here or moved inside allocateNewCluster method
        }
        VolumeUtility.updateRecordSize(volume, reservedSpace, dufsFileIndex, file.length());
    }

    public void readFile(String path, File file) throws IOException, DufsException {
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buffer = new byte[reservedSpace.getClusterSize()];
        // read bytes from every cluster in chain but the last
        int clusterIndex = dufsFile.getFirstClusterIndex();
        int prevClusterIndex = clusterIndex;
        while ((clusterIndex = VolumeUtility.findNextClusterIndex(volume, clusterIndex)) != -1) {
            VolumeUtility.readClusterFromVolume(volume, reservedSpace, clusterIndex, buffer);
            bos.write(buffer);
            prevClusterIndex = clusterIndex;
        }
        // read bytes from last cluster in chain
        byte[] lastClusterBuffer = new byte[(int) (dufsFile.getSize() % reservedSpace.getClusterSize())];
        VolumeUtility.readClusterFromVolume(volume, reservedSpace, prevClusterIndex, lastClusterBuffer);
        bos.write(lastClusterBuffer);
    }

    public void deleteFile(String path) throws DufsException, IOException {
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        VolumeUtility.deleteRecord(volume, reservedSpace, dufsFile, dufsFileIndex);
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

    public void printVolumeInfo() {

    }
    
    public void defragmentation() {

    }
}
