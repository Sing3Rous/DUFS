package com.dufs.offsets;

public class ReservedSpaceOffsets {
    public static final int RESERVED_SPACE_OFFSET = 0;
    public static final int DUFS_NOSE_SIGNATURE_OFFSET = 0;
    public static final int VOLUME_NAME_OFFSET = 4;
    public static final int CLUSTER_SIZE_OFFSET = 20;
    public static final int VOLUME_SIZE_OFFSET =  24;
    public static final int RESERVED_CLUSTERS_OFFSET = 32;
    public static final int CREATE_DATE_OFFSET = 36;
    public static final int CREATE_TIME_OFFSET = 38;
    public static final int LAST_DEFRAGMENTATION_DATE_OFFSET = 40;
    public static final int LAST_DEFRAGMENTATION_TIME_OFFSET = 42;
    public static final int NEXT_CLUSTER_INDEX_OFFSET = 44;
    public static final int FREE_CLUSTERS_OFFSET = 48;
    public static final int NEXT_RECORD_INDEX_OFFSET = 52;
    public static final int DUFS_TAIL_SIGNATURE_OFFSET = 56;

    public static final int RESERVED_SPACE_SIZE = 60;
}