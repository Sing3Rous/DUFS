package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;
import com.dufs.model.ClusterIndexElement;
import com.dufs.model.Record;
import com.dufs.model.ReservedSpace;
import com.dufs.utility.Serializer;
import com.dufs.utility.VolumeUtility;

import java.io.IOException;
import java.io.RandomAccessFile;

public class DufsImpl implements Dufs {

    @Override
    public RandomAccessFile mountVolume(String name, int clusterSize, long volumeSize) throws DufsException, IOException {
        if (name.length() > 8) {
            throw new DufsException("Volume name length has exceeded the limit.");
        }
        final int reservedClusters = VolumeUtility.clustersAmount(clusterSize, volumeSize);
        RandomAccessFile volume = new RandomAccessFile(name, "rw");
        ReservedSpace reservedSpace = new ReservedSpace(name.toCharArray(), clusterSize, volumeSize);
        volume.write(Serializer.serialize(reservedSpace));
        ClusterIndexElement[] clusterIndexList = new ClusterIndexElement[reservedClusters];
        volume.write(Serializer.serialize(clusterIndexList));
        Record[] recordsList = new Record[reservedClusters];
        volume.write(Serializer.serialize(recordsList));

        return volume;
    }

    @Override
    public RandomAccessFile attachVolume(String name) throws DufsException, IOException {
        return null;
    }

    @Override
    public void createFile(RandomAccessFile volume, String path, String name) {

    }

    @Override
    public void writeFile(RandomAccessFile volume, String path, String name, String content) {

    }

    @Override
    public void readFile(RandomAccessFile volume, String path, String name) {

    }

    @Override
    public void appendFile(RandomAccessFile volume, String path, String name, String content) {

    }

    @Override
    public void deleteFile(RandomAccessFile volume, String path, String file) {

    }

    @Override
    public void renameFile(RandomAccessFile volume, String path, String oldName, String newName) {

    }

    @Override
    public void moveFile(RandomAccessFile volume, String oldPath, String name, String newPath) {

    }

    @Override
    public void createDir(RandomAccessFile volume, String path, String name) {

    }

    @Override
    public void deleteDir(RandomAccessFile volume, String path, String name) {

    }

    @Override
    public void renameDir(RandomAccessFile volume, String path, String oldName, String newName) {

    }

    @Override
    public void moveDir(RandomAccessFile volume, String oldPath, String name, String newPath) {

    }

    @Override
    public void printDirContent(RandomAccessFile volume, String path) {

    }

    @Override
    public void defragmentation(RandomAccessFile volume) {

    }
}
