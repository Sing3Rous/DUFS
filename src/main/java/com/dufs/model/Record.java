package com.dufs.model;

import com.dufs.utility.DateUtility;
import com.dufs.utility.VolumeUtility;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Record {
    private final char[] name;
    private final short createDate;
    private final short createTime;
    private final int firstClusterIndex;
    private final short lastEditDate;
    private final short lastEditTime;
    private final long size;
    private final int parentDirectoryIndex;
    private final byte isFile;

    public char[] getName() {
        return name;
    }

    public short getCreateDate() {
        return createDate;
    }

    public short getCreateTime() {
        return createTime;
    }

    public int getFirstClusterIndex() {
        return firstClusterIndex;
    }

    public short getLastEditDate() {
        return lastEditDate;
    }

    public short getLastEditTime() {
        return lastEditTime;
    }

    public long getSize() {
        return size;
    }

    public int getParentDirectoryIndex() {
        return parentDirectoryIndex;
    }

    public byte getIsFile() {
        return isFile;
    }

    public Record(char[] name, int parentDirectoryIndex, byte isFile) {
        this.name = name;
        this.createDate = DateUtility.dateToShort(LocalDate.now());
        this.createTime = DateUtility.timeToShort(LocalDateTime.now());
        this.firstClusterIndex = 0/*VolumeUtility.findNextFreeCluster()*/;
        this.lastEditDate = this.createDate;
        this.lastEditTime = this.createTime;
        this.size = 0;
        this.parentDirectoryIndex = parentDirectoryIndex;
        this.isFile = isFile;
    }

    public Record(char[] name, short createDate, short createTime, int firstClusterIndex, short lastEditDate,
                  short lastEditTime, long size, int parentDirectoryIndex, byte isFile) {
        this.name = name;
        this.createDate = createDate;
        this.createTime = createTime;
        this.firstClusterIndex = firstClusterIndex;
        this.lastEditDate = lastEditDate;
        this.lastEditTime = lastEditTime;
        this.size = size;
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
