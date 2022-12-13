package com.dufs.utility;

import com.dufs.model.ReservedSpace;
import com.dufs.offsets.ReservedSpaceOffsets;

import java.io.IOException;
import java.io.RandomAccessFile;

public class VolumeUtility {
    public static int clustersAmount(int clusterSize, long volumeSize) {
        return ((int) Math.ceil((1.0 * volumeSize) / clusterSize));
    }

    public static ReservedSpace readReservedSpaceFromVolume(RandomAccessFile volume) throws IOException {
        volume.seek(ReservedSpaceOffsets.DUFS_NOSE_SIGNATURE_OFFSET);
        int noseSignature = volume.readInt();
        volume.seek(ReservedSpaceOffsets.VOLUME_NAME_OFFSET);
        char[] volumeName = new char[8];
        for (int i = 0; i < 8; ++i) {
            volumeName[i] = volume.readChar();
        }
        volume.seek(ReservedSpaceOffsets.CLUSTER_SIZE_OFFSET);
        int clusterSize = volume.readInt();
        volume.seek(ReservedSpaceOffsets.VOLUME_SIZE_OFFSET);
        long volumeSize = volume.readLong();
        volume.seek(ReservedSpaceOffsets.RESERVED_CLUSTERS_OFFSET);
        int reservedClusters = volume.readInt();
        volume.seek(ReservedSpaceOffsets.CREATE_DATE_OFFSET);
        short createDate = volume.readShort();
        volume.seek(ReservedSpaceOffsets.CREATE_TIME_OFFSET);
        short createTime = volume.readShort();
        volume.seek(ReservedSpaceOffsets.LAST_DEFRAGMENTATION_DATE_OFFSET);
        short lastDefragmentationDate = volume.readShort();
        volume.seek(ReservedSpaceOffsets.LAST_DEFRAGMENTATION_TIME_OFFSET);
        short lastDefragmentationTime = volume.readShort();
        volume.seek(ReservedSpaceOffsets.NEXT_CLUSTER_INDEX_OFFSET);
        int nextClusterIndex = volume.readInt();
        volume.seek(ReservedSpaceOffsets.FREE_CLUSTERS_OFFSET);
        int freeClusters= volume.readInt();
        volume.seek(ReservedSpaceOffsets.NEXT_RECORD_INDEX_OFFSET);
        int nextRecordIndex = volume.readInt();
        volume.seek(ReservedSpaceOffsets.DUFS_TAIL_SIGNATURE_OFFSET);
        int tailSignature = volume.readInt();
        volume.seek(0);
        return new ReservedSpace(noseSignature, volumeName, clusterSize, volumeSize, reservedClusters, createDate,
                createTime, lastDefragmentationDate, lastDefragmentationTime, nextClusterIndex,
                freeClusters, nextRecordIndex, tailSignature);
    }

    public static int findDirectoryIndex(RandomAccessFile volume, String path) {

    }

    public static int findNextFreeCluster(RandomAccessFile volume, int pos) {

    }
}
