package com.dufs.filesystem;

import com.dufs.model.ReservedSpace;
import com.dufs.offsets.ClusterIndexListOffsets;

public class SharedData {
    private ReservedSpace reservedSpace;
    private int recordListOffset;

    public ReservedSpace getReservedSpace() {
        return reservedSpace;
    }

    public void setReservedSpace(ReservedSpace reservedSpace) {
        this.reservedSpace = reservedSpace;
    }

    public int getRecordListOffset() {
        return recordListOffset;
    }

    public SharedData(ReservedSpace reservedSpace) {
        this.reservedSpace = reservedSpace;
        this.recordListOffset = computeRecordListOffset();
    }

    public int computeRecordListOffset() {
        return ClusterIndexListOffsets.CLUSTER_INDEX_LIST_OFFSET
                + reservedSpace.getReservedClusters() * ClusterIndexListOffsets.CLUSTER_INDEX_ELEMENT_SIZE;
    }
}
