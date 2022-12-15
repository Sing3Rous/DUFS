package com.dufs.utility;

import java.nio.file.FileSystems;
import java.util.Arrays;

public class Parser {
    public static String[] parsePath(String path) {
        return path.split(FileSystems.getDefault().getSeparator());
    }

    public static String joinPath(String[] directories) {
        return String.join(FileSystems.getDefault().getSeparator(), directories);
    }

    public static String[] parsePathBeforeFile(String path) {
        String[] records = parsePath(path);
        return Arrays.copyOf(records, records.length - 1); // remove the last element, which is the name of file
    }

    // could be removed if we add Pair<> entity
    public static String parseFileNameInPath(String path) {
        String[] records = parsePath(path);
        return records[records.length - 1];
    }

    public static boolean isRecordNameOk(String name) {
        return !(name.contains("\\") || name.contains("\"") || name.contains("*") || name.contains(":")
                || name.contains("<") || name.contains(">") || name.contains("?") || name.contains("|")
                || name.contains("/"));
    }
}
