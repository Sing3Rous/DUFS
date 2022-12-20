package com.dufs.utility;

import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.regex.Pattern;

public class Parser {
    public static String[] parsePath(String path) {
        return path.split(Pattern.quote(FileSystems.getDefault().getSeparator()));
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
        // check if name is all-null-symbols (\u0000)
        return !(name.contains("\\") || name.contains("\"") || name.contains("*") || name.contains(":")
                || name.contains("<") || name.contains(">") || name.contains("?") || name.contains("|")
                || name.contains("/"));
    }
}
