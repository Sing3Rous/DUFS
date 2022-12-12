package com.dufs.model;

public class Record {
    private char[] name = new char[32];
    private short createDate = 0;
    private short createTime = 0;
    private int firstClusterIndex = 0;
    private short lastEditDate = 0;
    private short lastEditTime = 0;
    private long size = 0;
    private int parentDirectoryIndex = 0;
    private byte attributes = 0;
    private boolean isFile = false; // perhaps could be moved to attributes
}
