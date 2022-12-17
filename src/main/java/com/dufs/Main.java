package com.dufs;

import com.dufs.exceptions.DufsException;
import com.dufs.filesystem.Dufs;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;

public class Main {
    public static void main(String[] args) throws IOException, DufsException {
        Dufs dufs = new Dufs();
        dufs.mountVolume("DISK_B", 4096, 4096000);
        //dufs.attachVolume("DISK_B");
        dufs.createFile("DISK_B", "qwe");
        dufs.writeFile("DISK_B\\qwe", new File("abc.txt"));
        dufs.readFile("DISK_B\\qwe", new File("qwe.txt"));
    }
}