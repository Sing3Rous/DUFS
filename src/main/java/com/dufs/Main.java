package com.dufs;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, DufsException {
        Dufs dufs = new Dufs();
        dufs.mountVolume("DISK_B", 4096, 4096000);
        dufs.createRecord("DISK_B", "papka", (byte) 0);
        dufs.createRecord("DISK_B", "failik", (byte) 1);
        dufs.createRecord("DISK_B\\papka", "failikvpapke", (byte) 1);
        dufs.createRecord("DISK_B\\papka",  "papkavpapke", (byte) 0);
        dufs.createRecord("DISK_B\\papka\\papkavpapke", "ocherednoi", (byte) 1);
        dufs.printDirectoryTree();
        dufs.writeFile("DISK_B\\papka\\papkavpapke\\ocherednoi", new File("abc.txt"));
        dufs.writeFile("DISK_B\\failik", new File("abc.txt"));
        dufs.appendFile("DISK_B\\papka\\papkavpapke\\ocherednoi", new File("abc.txt"));
        dufs.printVolumeRecords();
        System.out.println("***************************");
        dufs.renameFile("DISK_B\\papka\\papkavpapke\\ocherednoi", "kolpak");
        dufs.printVolumeRecords();
        System.out.println("***************************");
        dufs.moveFile("DISK_B\\papka\\papkavpapke\\kolpak", "DISK_B\\papka");
        dufs.deleteFile("DISK_B\\papka\\kolpak");
        dufs.printDirectoryTree();
        dufs.printVolumeRecords();
        dufs.createRecord("DISK_B\\papka", "NOVOE", (byte) 0);
        dufs.printDirectoryTree();
        dufs.moveFile("DISK_B\\failik", "DISK_B\\papka\\NOVOE");
        dufs.printDirectoryTree();
    }
}