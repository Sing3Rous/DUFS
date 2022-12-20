package com.dufs;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, DufsException {
        Dufs dufs = new Dufs();
        dufs.mountVolume("DISK_B", 4096, 4096000);
        //dufs.attachVolume("DISK_B");
        dufs.printVolumeInfo();
        dufs.createFile("DISK_B", "LOL");
        dufs.createFile("DISK_B", "ZOZA");
        dufs.printVolumeRecords();
        dufs.deleteFile("DISK_B\\ZOZA");
        dufs.printVolumeRecords();
        dufs.writeFile("DISK_B\\LOL", new File("abc.txt"));
        dufs.createFile("DISK_B", "kolpak");
        dufs.renameFile("DISK_B\\LOL", "kapusta");
        dufs.printVolumeRecords();
        dufs.writeFile("DISK_B\\kolpak", new File("abcde.txt"));
        dufs.printDirectoryTree();
        dufs.printVolumeRecords();
        //dufs.renameFile("DISK_B\\LOL", "kapusta");
        //dufs.printVolumeRecords();
//        dufs.printDirectoryContent("DISK_B");
//        dufs.printVolumeInfo();
//        dufs.appendFile("DISK_B\\LOL", new File("abc.txt"));
//        dufs.printDirectoryContent("DISK_B");
//        dufs.createFile("DISK_B", "kolpak");
//        dufs.printDirectoryContent("DISK_B");
//        dufs.readFile("DISK_B\\LOL", new File("LOL.txt"));
    }
}