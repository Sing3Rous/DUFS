package com.dufs.model;

public class Record {
    private char[] name = new char[32];
    private short createDate;
    private short createTime;
    private int firstClusterIndex;
    private short lastEditDate;
    private short lastEditTime;
    private long size;
    private int parentDirectoryIndex;
    private byte attributes;
    private byte isFile; // perhaps could be moved to attributes
}
