package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;

import java.io.IOException;
import java.io.RandomAccessFile;

public interface Dufs {
    RandomAccessFile mountVolume(String name,  int clusterSize, long volumeSize) throws DufsException, IOException;
    RandomAccessFile attachVolume(String name) throws DufsException, IOException;
    void createFile(RandomAccessFile volume, String path, String name);
    void writeFile(RandomAccessFile volume, String path, String name, String content);
    void readFile(RandomAccessFile volume, String path, String name);
    void appendFile(RandomAccessFile volume, String path, String name, String content);
    void deleteFile(RandomAccessFile volume, String path, String file);
    void renameFile(RandomAccessFile volume, String path, String oldName, String newName);
    void moveFile(RandomAccessFile volume, String oldPath, String name, String newPath);

    void createDir(RandomAccessFile volume, String path, String name);
    void deleteDir(RandomAccessFile volume, String path, String name);
    void renameDir(RandomAccessFile volume, String path, String oldName, String newName);
    void moveDir(RandomAccessFile volume, String oldPath, String name, String newPath);

    void printDirContent(RandomAccessFile volume, String path);
    void defragmentation(RandomAccessFile volume);
}
