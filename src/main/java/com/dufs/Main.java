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
        dufs.createFile("DISK_B", "qwe");
        dufs.printDirectoryContent("DISK_B");
        dufs.writeFile("DISK_B\\LOL", new File("abcde.txt"));
        dufs.printDirectoryContent("DISK_B");
        dufs.printVolumeInfo();
        //dufs.deleteFile("DISK_B\\LOL");
        dufs.appendFile("DISK_B\\LOL", new File("abc.txt"));
        dufs.printDirectoryContent("DISK_B");
        dufs.createFile("DISK_B", "kolpak");
        dufs.printDirectoryContent("DISK_B");
        dufs.readFile("DISK_B\\LOL", new File("LOL.txt"));
    }
}