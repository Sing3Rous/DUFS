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
        dufs.mountVolume("vol.DUFS", 4096, 4096000);
        dufs.createRecord("vol.DUFS", "papka", DIR);
        dufs.createRecord("vol.DUFS", "failik", FILE);
        dufs.createRecord("vol.DUFS\\papka", "failikvpapke", FILE);
        dufs.createRecord("vol.DUFS\\papka",  "papkavpapke", DIR);
        dufs.createRecord("vol.DUFS\\papka\\papkavpapke", "ocherednoi", FILE);
        dufs.writeFile("vol.DUFS\\papka\\papkavpapke\\ocherednoi", new File("abc.txt"));
        dufs.writeFile("vol.DUFS\\failik", new File("abc.txt"));
        dufs.appendFile("vol.DUFS\\papka\\papkavpapke\\ocherednoi", new File("abc.txt"));
        dufs.renameRecord("vol.DUFS\\papka\\papkavpapke\\ocherednoi", "kolpak", FILE);
        dufs.moveRecord("vol.DUFS\\papka\\papkavpapke\\kolpak", "vol.DUFS\\papka", FILE);
        dufs.deleteRecord("vol.DUFS\\papka\\kolpak", FILE);
        dufs.printDirectoryTree();
        dufs.createRecord("vol.DUFS\\papka", "NOVOE", DIR);
        dufs.printVolumeRecords();
        dufs.moveRecord("vol.DUFS\\failik", "vol.DUFS\\papka\\NOVOE", FILE);
        dufs.printDirectoryTree();
        dufs.moveRecord("vol.DUFS\\papka\\NOVOE", "vol.DUFS", DIR);
        dufs.printDirectoryTree();
        dufs.deleteRecord("vol.DUFS\\papka\\papkavpapke", DIR);
        dufs.printDirectoryTree();
        dufs.renameRecord("vol.DUFS\\NOVOE", "EWENOVEE", DIR);
        dufs.printDirectoryTree();
        dufs.createRecord("vol.DUFS\\papka", "masterskaya", DIR);
        dufs.createRecord("vol.DUFS\\papka\\masterskaya", "kabanchik", FILE);
        for (int i = 0; i < 10; ++i) {
            dufs.appendFile("vol.DUFS\\papka\\masterskaya\\kabanchik", new File("abc.txt"));
        }
        dufs.printDirectoryTree();
        dufs.printVolumeRecords();
        dufs.printVolumeInfo();
        dufs.defragmentation();
        dufs.printVolumeRecords();
    }
}