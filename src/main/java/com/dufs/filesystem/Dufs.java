package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;
import com.dufs.model.ClusterIndexList;
import com.dufs.model.RecordList;
import com.dufs.model.ReservedSpace;
import com.dufs.utility.VolumeUtility;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Dufs {
    private SharedData sharedData;

    public SharedData getSharedData() {
        return sharedData;
    }

    public RandomAccessFile mountVolume(String name, int clusterSize, long volumeSize) throws DufsException, IOException {
        if (name.length() > 8) {
            throw new DufsException("Volume name length has exceeded the limit.");
        }
        RandomAccessFile volume = new RandomAccessFile(name, "rw");
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
        RandomAccessFile volume = new RandomAccessFile(name, "rw");
        sharedData.setReservedSpace(VolumeUtility.readReservedSpaceFromVolume(volume));
        if (sharedData.getReservedSpace().getDufsNoseSignature() != 0x44554653
            || sharedData.getReservedSpace().getDufsTailSignature() != 0x4A455442) {
            throw new DufsException("Volume signature does not match.");
        }
        sharedData.setRecordListOffset(VolumeUtility.calculateRecordListOffset(sharedData.getReservedSpace()));
        return volume;
    }

    public void createFile(RandomAccessFile volume, String path, String name) {

    }

    
    public void writeFile(RandomAccessFile volume, String path, String name, String content) {

    }

    
    public void readFile(RandomAccessFile volume, String path, String name) {

    }

    
    public void appendFile(RandomAccessFile volume, String path, String name, String content) {

    }

    
    public void deleteFile(RandomAccessFile volume, String path, String file) {

    }

    
    public void renameFile(RandomAccessFile volume, String path, String oldName, String newName) {

    }

    
    public void moveFile(RandomAccessFile volume, String oldPath, String name, String newPath) {

    }

    
    public void createDir(RandomAccessFile volume, String path, String name) {

    }

    
    public void deleteDir(RandomAccessFile volume, String path, String name) {

    }

    
    public void renameDir(RandomAccessFile volume, String path, String oldName, String newName) {

    }

    
    public void moveDir(RandomAccessFile volume, String oldPath, String name, String newPath) {

    }

    
    public void printDirContent(RandomAccessFile volume, String path) {

    }

    
    public void defragmentation(RandomAccessFile volume) {

    }
}
