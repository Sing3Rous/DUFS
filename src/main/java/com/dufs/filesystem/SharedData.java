package com.dufs.filesystem;

import com.dufs.model.ReservedSpace;
import com.dufs.utility.VolumeUtility;

public class SharedData {
    private ReservedSpace reservedSpace;
    private long recordListOffset;

    public ReservedSpace getReservedSpace() {
        return reservedSpace;
    }

    public void setReservedSpace(ReservedSpace reservedSpace) {
        this.reservedSpace = reservedSpace;
    }

    public long getRecordListOffset() {
        return recordListOffset;
    }

    public void setRecordListOffset(long recordListOffset) {
        this.recordListOffset = recordListOffset;
    }

    public SharedData(ReservedSpace reservedSpace) {
        this.reservedSpace = reservedSpace;
        recordListOffset = VolumeUtility.calculateRecordListOffset(reservedSpace);
    }

    public void updateNextClusterIndex(int nextClusterIndex) {
        reservedSpace.setNextClusterIndex(nextClusterIndex);
    }
}
