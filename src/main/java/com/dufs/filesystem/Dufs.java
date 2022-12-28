package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;
import com.dufs.model.ClusterIndexList;
import com.dufs.model.Record;
import com.dufs.model.RecordList;
import com.dufs.model.ReservedSpace;
import com.dufs.utility.*;

import java.io.*;
import java.util.Arrays;

public class Dufs {
    private RandomAccessFile volume;
    private ReservedSpace reservedSpace;

    public RandomAccessFile getVolume() {
        return volume;
    }

    public void closeVolume() throws IOException {
        volume.close();
    }

    public void mountVolume(String path, int clusterSize, long nettoVolumeSize) throws DufsException, IOException {
        File file = new File(path);
        if (file.exists()) {
            throw new DufsException("Volume with such name already exists in this directory.");
        }
        String name = file.getName();
        if (name.length() > 8) {
            throw new DufsException("Volume name length has exceeded the limit.");
        }
        if (!Parser.isRecordNameOk(name)) {
            throw new DufsException("Volume name contains prohibited symbols.");
        }
        if (nettoVolumeSize > 1.1e12) {  // 1.1e12 == 1TiB == 1024GiB
            throw new DufsException("Volume size is too big.");
        }
        long bruttoVolumeSize = VolumeHelper.calculateVolumeSize(clusterSize, nettoVolumeSize);
        if (new File("/").getUsableSpace() < bruttoVolumeSize) {
            throw new DufsException("There is not enough space on disk.");
        }
        if (clusterSize % 4 != 0) {
            throw new DufsException("Cluster size cannot be divided by 4 directly.");
        }
        volume = new RandomAccessFile(file, "rw");
        volume.setLength(bruttoVolumeSize);
        reservedSpace = new ReservedSpace(name.toCharArray(), clusterSize, nettoVolumeSize);
        volume.write(reservedSpace.serialize());
        ClusterIndexList clusterIndexList = new ClusterIndexList(clusterSize, nettoVolumeSize);
        volume.write(clusterIndexList.serialize());
        VolumeIO.initializeRootCluster(volume);
        RecordList recordList = new RecordList(clusterSize, nettoVolumeSize);
        volume.write(recordList.serialize());
        VolumeIO.initializeRootRecord(volume, reservedSpace);
    }

    public void attachVolume(String path) throws DufsException, IOException {
        File file = new File(path);;
        if (!file.exists() || file.isDirectory()) {
            throw new DufsException("There is no volume with such name in this directory.");
        }
        volume = new RandomAccessFile(file, "rw");
        reservedSpace = VolumeIO.readReservedSpaceFromVolume(volume);
        if (reservedSpace.getDufsNoseSignature() != 0x44554653 || reservedSpace.getDufsTailSignature() != 0x4A455442) {
            throw new DufsException("Volume signature does not match.");
        }
    }

    public void createRecord(String path, String name, byte isFile) throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
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
        reservedSpace = VolumeIO.readReservedSpaceFromVolume(volume);
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        Record directory = VolumeIO.readRecordFromVolume(volume, reservedSpace, directoryIndex);
        if (!VolumeHelper.isNameUniqueInDirectory(volume, reservedSpace, directoryIndex, name.toCharArray(), isFile)) {
            throw new DufsException(recordType + " with such name already contains in this path.");
        }
        if (!VolumeHelper.enoughSpace(reservedSpace, 0)) {
            throw new DufsException("Not enough space in the volume to create new " + recordType + ".");
        }
        int firstClusterIndex = reservedSpace.getNextClusterIndex();
        VolumeIO.updateVolumeNextClusterIndex(volume, reservedSpace.getNextClusterIndex());
        int recordIndex = reservedSpace.getNextRecordIndex();
        int directoryOrderNumber = VolumeUtility.addRecordIndexInDirectoryCluster(volume, reservedSpace,
                recordIndex, directory.getFirstClusterIndex());
        Record file = new Record(name.toCharArray(), firstClusterIndex, directoryIndex, directoryOrderNumber, isFile);
        VolumeIO.writeRecordToVolume(volume, reservedSpace, recordIndex, file);
        reservedSpace.setNextRecordIndex(VolumeUtility.findNextFreeRecordIndex(volume, reservedSpace));
        VolumeIO.updateVolumeNextRecordIndex(volume, reservedSpace.getNextRecordIndex());
        VolumeUtility.createClusterIndexChain(volume, reservedSpace, firstClusterIndex);
        VolumeIO.updateVolumeFreeClusters(volume, reservedSpace.getFreeClusters() - 1);
    }

    /*
     * deletes current content in Dufs::file, then
     * writes data from `java.io.File` into the clusters in DUFS
     * currently it supports only writing data from the external file
     */
    public void writeFile(String path, File file) throws DufsException, IOException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        if (!VolumeHelper.enoughSpace(reservedSpace, file.length())) {
            throw new DufsException("Not enough space in the volume to write this content in file.");
        }
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeIO.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        VolumeIO.cleanFileData(volume, reservedSpace, dufsFileIndex);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        byte[] buffer = new byte[reservedSpace.getClusterSize()];
        int clusterIndex = dufsFile.getFirstClusterIndex();
        int bytes;
        while ((bytes = bis.read(buffer)) != -1) {
            VolumeUtility.allocateInCluster(volume, reservedSpace, clusterIndex, buffer, 0);
            if (bytes == reservedSpace.getClusterSize()) {
                clusterIndex = VolumeUtility.updateClusterIndexChain(volume, reservedSpace, clusterIndex);
                VolumeIO.updateVolumeNextClusterIndex(volume, reservedSpace.getNextClusterIndex());
            }
        }
        VolumeIO.updateRecordSize(volume, reservedSpace, dufsFileIndex, file.length() + dufsFile.getSize());
        VolumeIO.updateRecordLastEdit(volume, reservedSpace, dufsFileIndex);
        bis.close();
    }

    /*
     * appends data to the file which already contains some data
     */
    public void appendFile(String path, File file) throws  DufsException, IOException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        if (!VolumeHelper.enoughSpace(reservedSpace, file.length())) {
            throw new DufsException("Not enough space in the volume to write this content in file.");
        }
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeIO.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
        int bytesLeftInCluster = reservedSpace.getClusterSize() - (int) (dufsFile.getSize() % reservedSpace.getClusterSize());
        int firstClusterIndex = dufsFile.getFirstClusterIndex();
        byte[] buffer = new byte[reservedSpace.getClusterSize()];
        int lastClusterIndex = VolumeUtility.findLastClusterIndexInChain(volume, firstClusterIndex);
        byte[] lastClusterBuffer = new byte [bytesLeftInCluster];
        int clusterIndex = lastClusterIndex;
        int bytes;
        // initially allocate content in the end of the last cluster
        if ((bytes = bis.read(lastClusterBuffer)) != -1) {
            VolumeUtility.allocateInCluster(volume, reservedSpace, lastClusterIndex, lastClusterBuffer,
                    reservedSpace.getClusterSize() - bytesLeftInCluster);
            if (bytes == bytesLeftInCluster) {
                clusterIndex = VolumeUtility.updateClusterIndexChain(volume, reservedSpace, clusterIndex);
                VolumeIO.updateVolumeNextClusterIndex(volume, reservedSpace.getNextClusterIndex());
            }
        }
        // then allocate content in new clusters
        while ((bytes = bis.read(buffer)) != -1) {
            VolumeUtility.allocateInCluster(volume, reservedSpace, clusterIndex, buffer, 0);
            if (bytes == reservedSpace.getClusterSize()) {
                clusterIndex = VolumeUtility.updateClusterIndexChain(volume, reservedSpace, clusterIndex);
                VolumeIO.updateVolumeNextClusterIndex(volume, reservedSpace.getNextClusterIndex());
            }
        }
        VolumeIO.updateRecordSize(volume, reservedSpace, dufsFileIndex, file.length() + dufsFile.getSize());
        VolumeIO.updateRecordLastEdit(volume, reservedSpace, dufsFileIndex);
        bis.close();
    }

    public void readFile(String path, File file) throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        int dufsFileIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        Record dufsFile = VolumeIO.readRecordFromVolume(volume, reservedSpace, dufsFileIndex);
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buffer = new byte[reservedSpace.getClusterSize()];
        // read bytes from every cluster in the chain but the last
        int clusterIndex = dufsFile.getFirstClusterIndex();
        int prevClusterIndex = clusterIndex;
        while ((clusterIndex = VolumeUtility.findNextClusterIndexInChain(volume, clusterIndex)) != -1) {
            VolumeIO.readClusterFromVolume(volume, reservedSpace, prevClusterIndex, buffer);
            bos.write(buffer);
            prevClusterIndex = clusterIndex;
        }
        // read bytes from the last cluster in the chain
        byte[] lastClusterBuffer = new byte[(int) (dufsFile.getSize() % reservedSpace.getClusterSize())];
        VolumeIO.readClusterFromVolume(volume, reservedSpace, prevClusterIndex, lastClusterBuffer);
        bos.write(lastClusterBuffer);
        bos.close();
    }

    public void deleteRecord(String path, byte isFile) throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        int dufsRecordIndex;
        if (isFile == 1) {
            dufsRecordIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        } else {
            dufsRecordIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
            if (!VolumeHelper.isDirectoryEmpty(volume, reservedSpace, dufsRecordIndex)) {
                throw new DufsException("Directory is not empty");
            }
        }
        Record dufsRecord = VolumeIO.readRecordFromVolume(volume, reservedSpace, dufsRecordIndex);
        if (!VolumeHelper.recordExists(volume, dufsRecord.getFirstClusterIndex()) && (dufsRecord.getIsFile() == isFile)) {
            throw new DufsException("File does not exist.");
        }
        VolumeUtility.deleteRecord(volume, reservedSpace, dufsRecord, dufsRecordIndex);
        int freeClusters;
        if (isFile == 1) {
            freeClusters = reservedSpace.getFreeClusters()
                    + Math.max(1, VolumeHelper.howMuchClustersNeeds(reservedSpace, dufsRecord.getSize()));
        } else {
            freeClusters = reservedSpace.getFreeClusters()
                    + Math.max(1, VolumeHelper.howMuchClustersDirectoryTakes(volume, reservedSpace, dufsRecordIndex));
        }
        reservedSpace.setFreeClusters(freeClusters);
        VolumeIO.updateVolumeFreeClusters(volume, freeClusters);
    }

    public void renameRecord(String path, String newName, byte isFile) throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        if (newName.length() > 32) {
            throw new DufsException("New name length has exceeded the limit.");
        }
        if (!Parser.isRecordNameOk(newName)) {
            throw new DufsException("New name contains prohibited symbols.");
        }
        int parentDirectoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, Parser.joinPath(Parser.parsePathBeforeFile(path)));
        if (!VolumeHelper.isNameUniqueInDirectory(volume, reservedSpace, parentDirectoryIndex, newName.toCharArray(), isFile)) {
            throw new DufsException("Record with such name and type already contains in this path.");
        }
        int dufsRecordIndex;
        if (isFile == 1) {
            dufsRecordIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        } else {
            dufsRecordIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        }
        Record dufsFile = VolumeIO.readRecordFromVolume(volume, reservedSpace, dufsRecordIndex);
        if (!VolumeHelper.recordExists(volume, dufsFile.getFirstClusterIndex()) && (dufsFile.getIsFile() == isFile)) {
            throw new DufsException("Record does not exist.");
        }
        VolumeIO.updateRecordName(volume, reservedSpace, dufsRecordIndex, Arrays.copyOf(newName.toCharArray(), 32));
        VolumeIO.updateRecordLastEdit(volume, reservedSpace, dufsRecordIndex);
    }

    public void moveRecord(String path, String newPath, byte isFile) throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        int dufsRecordIndex;
        if (isFile == 1) {
            dufsRecordIndex = VolumeUtility.findFileIndex(volume, reservedSpace, path);
        } else {
            dufsRecordIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        }
        Record dufsRecord = VolumeIO.readRecordFromVolume(volume, reservedSpace, dufsRecordIndex);
        if (!VolumeHelper.recordExists(volume, dufsRecord.getFirstClusterIndex()) && (dufsRecord.getIsFile() == isFile)) {
            throw new DufsException("Record does not exist.");
        }
        int newDirectoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, newPath);
        Record newDirectory = VolumeIO.readRecordFromVolume(volume, reservedSpace, newDirectoryIndex);
        int newDirectoryIndexOrderNumber = VolumeUtility.addRecordIndexInDirectoryCluster(volume, reservedSpace,
                dufsRecordIndex, newDirectory.getFirstClusterIndex());
        VolumeUtility.removeRecordIndexFromDirectoryCluster(volume, reservedSpace,
                dufsRecord.getParentDirectoryIndex(), dufsRecord.getParentDirectoryIndexOrderNumber());
        VolumeIO.updateRecordParentDirectory(volume, reservedSpace, dufsRecordIndex, newDirectoryIndex, newDirectoryIndexOrderNumber);
        VolumeIO.updateRecordLastEdit(volume, reservedSpace, dufsRecordIndex);
    }
    
    public void printDirectoryContent(String path) throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        int directoryIndex = VolumeUtility.findDirectoryIndex(volume, reservedSpace, path);
        PrintUtility.printRecordsInDirectory(volume, reservedSpace, directoryIndex);
    }

    public void printVolumeInfo() throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        ReservedSpace volumeReservedSpace = VolumeIO.readReservedSpaceFromVolume(volume);
        String volumeName = new String(volumeReservedSpace.getVolumeName()).replace("\u0000", "");
        System.out.println("Volume name: " + volumeName);
        System.out.println("Volume size in Kib: " + PrintUtility.bytes2KiB(volumeReservedSpace.getVolumeSize()));
        int[] createDate = DateUtility.shortToDate(volumeReservedSpace.getCreateDate());
        int[] createTime = DateUtility.shortToTime(volumeReservedSpace.getCreateTime());
        int[] lastDefragmentationDate = DateUtility.shortToDate(volumeReservedSpace.getLastDefragmentationDate());
        int[] lastDefragmentationTime = DateUtility.shortToTime(volumeReservedSpace.getLastDefragmentationTime());
        System.out.println("Create date: " + createDate[2] + "." + createDate[1] + "." + createDate[0]);
        System.out.println("Create time: " + createTime[0] + ":" + createTime[1] + ":" + createTime[2]);
        System.out.println("Free size in Kib: " +
                PrintUtility.bytes2KiB((long) volumeReservedSpace.getFreeClusters() * volumeReservedSpace.getClusterSize()));
        System.out.println("Last defragmentation date: " + lastDefragmentationDate[2]
                + "." + lastDefragmentationDate[1] + "." + lastDefragmentationDate[0]);
        System.out.println("Last defragmentation time: " + lastDefragmentationTime[0]
                + ":" + lastDefragmentationTime[1] + ":" + lastDefragmentationTime[2]);
    }

    public void printVolumeRecords() throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        PrintUtility.printRecords(volume, reservedSpace);
    }

    public void printDirectoryTree() throws IOException, DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
        String rootName = new String(reservedSpace.getVolumeName()).replace("\u0000", "");
        System.out.println("|" + rootName);
        PrintUtility.dfsPrintRecords(volume, reservedSpace, 0, 1);
        System.out.println();
    }
    
    public void defragmentation() throws DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
    }

    public void bake() throws DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
    }

    public void unbake() throws DufsException {
        if (volume == null) {
            throw new DufsException("Volume has not found.");
        }
    }
}