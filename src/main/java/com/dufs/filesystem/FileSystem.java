package com.dufs.filesystem;

import java.io.RandomAccessFile;

public interface FileSystem {
    RandomAccessFile MountVolume(String name, long volumeSize, long clusterSize);
    RandomAccessFile AttachVolume(String name);
    void CreateFile(RandomAccessFile volume, Directory dir, String name);
    void WriteFile(RandomAccessFile volume, Directory dir, String name, String content);
    void ReadFile(RandomAccessFile volume, Directory dir, String name);
    void AppendFile(RandomAccessFile volume, Directory dir, String name, String content);
    void DeleteFile(RandomAccessFile volume, Directory dir, String file);
    void RenameFile(RandomAccessFile volume, Directory dir, String oldName, String newName);
    void MoveFile(RandomAccessFile volume, Directory oldDir, String name, Directory newDir);

    void CreateDir(RandomAccessFile volume, Directory dir, String name);
    void DeleteDir(RandomAccessFile volume, Directory dir, String name);
    void RenameDir(RandomAccessFile volume, Directory dir, String oldName, String newName);
    void MoveDir(RandomAccessFile volume, Directory oldDir, String name, Directory newDir);

    void PrintDirContent(RandomAccessFile volume, Directory dir);
    void Defragmentation(RandomAccessFile volume);
}
