package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;
import com.dufs.model.ClusterIndexList;
import com.dufs.model.Record;
import com.dufs.model.RecordList;
import com.dufs.model.ReservedSpace;
import com.dufs.offsets.ReservedSpaceOffsets;
import com.dufs.utility.DateUtility;
import com.dufs.utility.Parser;
import com.dufs.utility.PrintUtility;
import com.dufs.utility.VolumeUtility;

import java.io.*;
import java.util.Arrays;
import java.util.Date;

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

    public void createRecord(String path, String name, byte isFile) throws IOException, DufsException {
        String recordType = "";
        if (isFile == 1) {
            recordType += "File";
        } else {
            recordType += "Directory";
        }
        if (name.length() > 32) {
            throw new DufsException(recordType + " name length has exceeded the limit.");
        }
        if (!Parser.isRecordNameOk(name)) {
            throw new DufsException(recordType + " name contains prohibited symbols.");
        }
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        if (!VolumeUtility.isNameUniqueInDirectory(volume, reservedSpace, directoryIndex, name.toCharArray(), (byte) 1)) {
            throw new DufsException(recordType + " with such name already contains in this path.");
        }
        if (!VolumeUtility.enoughSpace(reservedSpace, 0)) {
            throw new DufsException("Not enough space in the volume to create new " + recordType + ".");
        }
        int firstClusterIndex = reservedSpace.getNextClusterIndex();
        reservedSpace.setNextClusterIndex(VolumeUtility.findNextFreeClusterIndex(volume, reservedSpace));
        VolumeUtility.updateVolumeNextClusterIndex(volume, reservedSpace.getNextClusterIndex());
        int recordIndex = reservedSpace.getNextRecordIndex();
        int directoryOrderNumber = VolumeUtility.addRecordIndexInDirectoryCluster(volume, reservedSpace, recordIndex, directoryIndex);
        Record file = new Record(name.toCharArray(), firstClusterIndex, directoryIndex, directoryOrderNumber, isFile);
        VolumeUtility.writeRecordToVolume(volume, reservedSpace, recordIndex, file);
        reservedSpace.setNextRecordIndex(VolumeUtility.findNextFreeRecordIndex(volume, reservedSpace));
        VolumeUtility.updateVolumeNextRecordIndex(volume, reservedSpace.getNextRecordIndex());
        VolumeUtility.createClusterIndexChain(volume, reservedSpace, firstClusterIndex);
        VolumeUtility.updateVolumeFreeClusters(volume, reservedSpace.getFreeClusters() - 1);
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
                VolumeUtility.allocateInEmptyCluster(volume, reservedSpace, clusterIndex, buffer);
                clusterIndex = VolumeUtility.updateClusterChain(volume, reservedSpace, clusterIndex);
                VolumeUtility.updateVolumeNextClusterIndex(volume, reservedSpace.getNextClusterIndex());
            } else {
                VolumeUtility.allocateInEmptyCluster(volume, reservedSpace, clusterIndex, buffer);
            }
        }
        VolumeUtility.updateRecordSize(volume, reservedSpace, dufsFileIndex, file.length() + dufsFile.getSize());
        bis.close();
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
        int bytes;
        while ((bytes = bis.read(buffer)) != -1) {
            if (bytes == reservedSpace.getClusterSize()) {
                VolumeUtility.allocateInEmptyCluster(volume, reservedSpace, clusterIndex, buffer);
                clusterIndex = VolumeUtility.updateClusterChain(volume, reservedSpace, clusterIndex);
                VolumeUtility.updateVolumeNextClusterIndex(volume, reservedSpace.getNextClusterIndex());
            } else {
                VolumeUtility.allocateInEmptyCluster(volume, reservedSpace, clusterIndex, buffer);
            }
        }
        VolumeUtility.updateRecordSize(volume, reservedSpace, dufsFileIndex, file.length() + dufsFile.getSize());
        bis.close();
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
        bos.close();
    }

    public void deleteFile(String path) throws DufsException, IOException {
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        VolumeUtility.deleteRecord(volume, reservedSpace, dufsFile, dufsFileIndex);
        int freeClusters = reservedSpace.getFreeClusters()
                + Math.max(1, VolumeUtility.howMuchClustersNeeds(reservedSpace, dufsFile.getSize()));
        reservedSpace.setFreeClusters(freeClusters);
        VolumeUtility.updateVolumeFreeClusters(volume, freeClusters);
    }

    public void renameFile(String path, String newName) throws IOException, DufsException {
        if (newName.length() > 32) {
            throw new DufsException("New file name length has exceeded the limit.");
        }
        if (!Parser.isRecordNameOk(newName)) {
            throw new DufsException("New file name contains prohibited symbols.");
        }
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, Parser.joinPath(Parser.parsePathBeforeFile(path)));
        if (!VolumeUtility.isNameUniqueInDirectory(volume, reservedSpace, directoryIndex, newName.toCharArray(), (byte) 1)) {
            throw new DufsException("File with such name already contains in this path.");
        }
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        VolumeUtility.updateRecordName(volume, reservedSpace, dufsFileIndex, Arrays.copyOf(newName.toCharArray(), 32));
    }
    
    public void moveFile(String path, String newPath) throws IOException, DufsException {
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        if (!VolumeUtility.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == 1)) {
            throw new DufsException("File does not exist.");
        }
        int newDirectoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, newPath);
        int newDirectoryIndexOrderNumber = VolumeUtility.addRecordIndexInDirectoryCluster(volume, reservedSpace, dufsFileIndex, newDirectoryIndex);
        VolumeUtility.removeRecordIndexFromDirectoryCluster(volume, reservedSpace,
                dufsFile.getParentDirectoryIndex(), dufsFile.getParentDirectoryIndexOrderNumber());
        VolumeUtility.updateRecordParentDirectory(volume, reservedSpace, dufsFileIndex, newDirectoryIndex, newDirectoryIndexOrderNumber);
    }
    
    public void deleteDir(String path, String name) throws IOException, DufsException {
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        if (!VolumeUtility.isDirectoryEmpty(volume, reservedSpace, directoryIndex)) {
            throw new DufsException("Directory is not empty");
        }
        Record dufsDirectory = VolumeUtility.readRecordFromVolume(volume, reservedSpace, directoryIndex);
        VolumeUtility.deleteRecord(volume, reservedSpace, dufsDirectory, directoryIndex);
        int freeClusters = reservedSpace.getFreeClusters()
                + Math.max(1, VolumeUtility.howMuchClusterTakes(volume, reservedSpace, directoryIndex));
        reservedSpace.setFreeClusters(freeClusters);
        VolumeUtility.updateVolumeFreeClusters(volume, freeClusters);
    }
    
    public void renameDir(String path, String newName) throws DufsException, IOException {
        if (newName.length() > 32) {
            throw new DufsException("New file name length has exceeded the limit.");
        }
        if (!Parser.isRecordNameOk(newName)) {
            throw new DufsException("New file name contains prohibited symbols.");
        }
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, Parser.joinPath(Parser.parsePathBeforeFile(path)));
        if (!VolumeUtility.isNameUniqueInDirectory(volume, reservedSpace, directoryIndex, newName.toCharArray(), (byte) 0)) {
            throw new DufsException("File with such name already contains in this path.");
        }
        Record dufsDirectory = VolumeUtility.readRecordFromVolume(volume, reservedSpace, directoryIndex);
        if (!VolumeUtility.recordExists(volume, dufsDirectory.getFirstClusterIndex()) && (dufsDirectory.getIsFile() == 0)) {
            throw new DufsException("File does not exist.");
        }
        VolumeUtility.updateRecordName(volume, reservedSpace, directoryIndex, Arrays.copyOf(newName.toCharArray(), 32));
    }
    
    public void moveDir(String path, String newPath) throws IOException, DufsException {
        int dufsDirectoryIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsDirectory = VolumeUtility.readRecordFromVolume(volume, reservedSpace, dufsDirectoryIndex);
        if (!VolumeUtility.recordExists(volume, dufsDirectory.getFirstClusterIndex()) && (dufsDirectory.getIsFile() == 0)) {
            throw new DufsException("File does not exist.");
        }
        int newDirectoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, newPath);
        int newDirectoryIndexOrderNumber = VolumeUtility.addRecordIndexInDirectoryCluster(volume, reservedSpace, dufsDirectoryIndex, newDirectoryIndex);
        VolumeUtility.removeRecordIndexFromDirectoryCluster(volume, reservedSpace,
                dufsDirectory.getParentDirectoryIndex(), dufsDirectory.getParentDirectoryIndexOrderNumber());
        VolumeUtility.updateRecordParentDirectory(volume, reservedSpace, dufsDirectoryIndex, newDirectoryIndex, newDirectoryIndexOrderNumber);
    }
    
    public void printDirectoryContent(String path) throws IOException, DufsException {
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        PrintUtility.printRecordsInDirectory(volume, reservedSpace, directoryIndex);
    }

    public void printVolumeInfo() throws IOException {
        ReservedSpace volumeReservedSpace = VolumeUtility.readReservedSpaceFromVolume(volume);
        String volumeName = new String(volumeReservedSpace.getVolumeName()).replace("\u0000", "");
        System.out.println("Volume name: " + volumeName);
        System.out.println("Volume size in Kib: " + PrintUtility.bytes2KiB(volumeReservedSpace.getVolumeSize()));
        int[] createDate = DateUtility.shortToDate(volumeReservedSpace.getCreateDate());
        int[] createTime = DateUtility.shortToTime(volumeReservedSpace.getCreateTime());
        int[] lastDefragmentationDate = DateUtility.shortToDate(volumeReservedSpace.getLastDefragmentationDate());
        int[] lastDefragmentationTime = DateUtility.shortToTime(volumeReservedSpace.getLastDefragmentationTime());
        System.out.println("Create date: " + createDate[0] + "." + createDate[1] + "." + createDate[2]);
        System.out.println("Create time: " + createTime[0] + ":" + createTime[1] + ":" + createTime[2]);
        System.out.println("Free size in Kib: " +
                PrintUtility.bytes2KiB((long) volumeReservedSpace.getFreeClusters() * volumeReservedSpace.getClusterSize()));
        System.out.println("Last defragmentation date: " + lastDefragmentationDate[0]
                + "." + lastDefragmentationDate[1] + "." + lastDefragmentationDate[2]);
        System.out.println("Last defragmentation time: " + lastDefragmentationTime[0]
                + ":" + lastDefragmentationTime[1] + ":" + lastDefragmentationTime[2]);
    }

    public void printVolumeRecords() throws IOException {
        PrintUtility.printRecords(volume, reservedSpace);
    }

    public void printDirectoryTree() throws IOException {
        String rootName = new String(reservedSpace.getVolumeName()).replace("\u0000", "");
        System.out.println(rootName);
        PrintUtility.dfsPrintRecords(volume, reservedSpace, 0, 1);
    }
    
    public void defragmentation() {

    }
}
