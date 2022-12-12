package com.dufs.filesystem;

import java.io.RandomAccessFile;

public class FileSystemImpl implements FileSystem {

    @Override
    public RandomAccessFile MountVolume(String name, long volumeSize, long clusterSize) {
        return null;
    }

    @Override
    public RandomAccessFile AttachVolume(String name) {
        return null;
    }

    @Override
    public void CreateFile(RandomAccessFile volume, Directory dir, String name) {

    }

    @Override
    public void WriteFile(RandomAccessFile volume, Directory dir, String name, String content) {

    }

    @Override
    public void ReadFile(RandomAccessFile volume, Directory dir, String name) {

    }

    @Override
    public void AppendFile(RandomAccessFile volume, Directory dir, String name, String content) {

    }

    @Override
    public void DeleteFile(RandomAccessFile volume, Directory dir, String file) {

    }

    @Override
    public void RenameFile(RandomAccessFile volume, Directory dir, String oldName, String newName) {

    }

    @Override
    public void MoveFile(RandomAccessFile volume, Directory oldDir, String name, Directory newDir) {

    }

    @Override
    public void CreateDir(RandomAccessFile volume, Directory dir, String name) {

    }

    @Override
    public void DeleteDir(RandomAccessFile volume, Directory dir, String name) {

    }

    @Override
    public void RenameDir(RandomAccessFile volume, Directory dir, String oldName, String newName) {

    }

    @Override
    public void MoveDir(RandomAccessFile volume, Directory oldDir, String name, Directory newDir) {

    }

    @Override
    public void PrintDirContent(RandomAccessFile volume, Directory dir) {

    }

    @Override
    public void Defragmentation(RandomAccessFile volume) {

    }
}
