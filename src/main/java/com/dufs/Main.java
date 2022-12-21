package com.dufs;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, DufsException {
        Dufs dufs = new Dufs();
        final byte FILE = 1;
        final byte DIR = 0;
        dufs.mountVolume("DISK_B", 4096, 4096000);
        dufs.createRecord("DISK_B", "papka", DIR);
        dufs.createRecord("DISK_B", "failik", FILE);
        dufs.createRecord("DISK_B\\papka", "failikvpapke", FILE);
        dufs.createRecord("DISK_B\\papka",  "papkavpapke", DIR);
        dufs.createRecord("DISK_B\\papka\\papkavpapke", "ocherednoi", FILE);
        dufs.writeFile("DISK_B\\papka\\papkavpapke\\ocherednoi", new File("abc.txt"));
        dufs.writeFile("DISK_B\\failik", new File("abc.txt"));
        dufs.appendFile("DISK_B\\papka\\papkavpapke\\ocherednoi", new File("abc.txt"));
        dufs.renameRecord("DISK_B\\papka\\papkavpapke\\ocherednoi", "kolpak", FILE);
        dufs.moveRecord("DISK_B\\papka\\papkavpapke\\kolpak", "DISK_B\\papka", FILE);
        dufs.deleteRecord("DISK_B\\papka\\kolpak", FILE);
        dufs.printDirectoryTree();
        dufs.createRecord("DISK_B\\papka", "NOVOE", DIR);
        dufs.printVolumeRecords();
        dufs.moveRecord("DISK_B\\failik", "DISK_B\\papka\\NOVOE", FILE);
        dufs.printDirectoryTree();
        dufs.moveRecord("DISK_B\\papka\\NOVOE", "DISK_B", DIR);
        dufs.printDirectoryTree();
        dufs.deleteRecord("DISK_B\\papka\\papkavpapke", DIR);
        dufs.printDirectoryTree();
        dufs.renameRecord("DISK_B\\NOVOE", "EWENOVEE", DIR);
        dufs.printDirectoryTree();
        dufs.createRecord("DISK_B\\papka", "masterskaya", DIR);
        dufs.createRecord("DISK_B\\papka\\masterskaya", "kabanchik", FILE);
        for (int i = 0; i < 10; ++i) {
            dufs.appendFile("DISK_B\\papka\\masterskaya\\kabanchik", new File("abc.txt"));
        }
        dufs.printDirectoryTree();
        dufs.printVolumeRecords();
        dufs.printVolumeInfo();
    }
}