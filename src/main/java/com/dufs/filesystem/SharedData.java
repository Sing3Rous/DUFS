package com.dufs.filesystem;

import com.dufs.model.ReservedSpace;
import com.dufs.offsets.ClusterIndexListOffsets;
import com.dufs.utility.VolumeUtility;

public class SharedData {
    private static ReservedSpace reservedSpace;
    private static long recordListOffset;

    public ReservedSpace getReservedSpace() {
        return reservedSpace;
    }

    public void setReservedSpace(ReservedSpace reservedSpace) {
        SharedData.reservedSpace = reservedSpace;
    }

    public long getRecordListOffset() {
        return recordListOffset;
    }

    public void setRecordListOffset(long recordListOffset) {
        SharedData.recordListOffset = recordListOffset;
    }

    public SharedData(ReservedSpace reservedSpace) {
        SharedData.reservedSpace = reservedSpace;
        recordListOffset = VolumeUtility.calculateRecordListOffset(reservedSpace);
    }

    public static void updateNextClusterIndex(int nextClusterIndex) {
        reservedSpace.setNextClusterIndex(nextClusterIndex);
    }
}
