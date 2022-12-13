package com.dufs.model;

import com.dufs.utility.DateUtility;
import com.dufs.utility.VolumeUtility;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Record {
    private char[] name;
    private short createDate;
    private short createTime;
    private int firstClusterIndex;
    private short lastEditDate;
    private short lastEditTime;
    private long size;
    private int parentDirectoryIndex;
    private byte isFile;

    public Record(char[] name, int parentDirectoryIndex, byte isFile) {
        this.name = name;
        this.createDate = DateUtility.dateToShort(LocalDate.now());
        this.createTime = DateUtility.timeToShort(LocalDateTime.now());
        //this.firstClusterIndex = VolumeUtility.findNextFreeCluster();
        this.lastEditDate = this.createDate;
        this.lastEditTime = this.createTime;
        this.size = 0;
        this.parentDirectoryIndex = parentDirectoryIndex;
        this.isFile = isFile;
    }

    public byte[] serialize() {
        final int bytesCount = 89;
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesCount);
        for (int i = 0; i < 32; ++i) {
            if (i < name.length) {
                buffer.putChar(name[i]);
            } else {
                buffer.putChar('\u0000');
            }
        }
        buffer.putShort(createDate);
        buffer.putShort(createTime);
        buffer.putShort(lastEditDate);
        buffer.putShort(lastEditTime);
        buffer.putInt(firstClusterIndex);
        buffer.putLong(size);
        buffer.putInt(parentDirectoryIndex);
        buffer.put(isFile);
        buffer.position(0);
        byte[] tmp = new byte[bytesCount];
        buffer.get(tmp, 0, bytesCount);
        return tmp;
    }
}
