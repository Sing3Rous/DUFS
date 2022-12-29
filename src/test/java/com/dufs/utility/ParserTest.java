package com.dufs.utility;

import org.junit.jupiter.api.Test;

import java.nio.file.FileSystems;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    @Test
    void parsePath() {
        String separator = FileSystems.getDefault().getSeparator();
        String path = "vol.dufs" + separator
                + "folder1" + separator
                + "folder2" + separator
                + "file";
        String[] parsedPath = Parser.parsePath(path);
        assertEquals(4, parsedPath.length);
        assertEquals("vol.dufs", parsedPath[0]);
        assertEquals("folder1", parsedPath[1]);
        assertEquals("folder2", parsedPath[2]);
        assertEquals("file", parsedPath[3]);
    }

    @Test
    void joinPath() {
        String[] parsedPath = {"vol.dufs", "folder1", "folder2", "file"};
        String path = Parser.joinPath(parsedPath);
        String separator = FileSystems.getDefault().getSeparator();
        assertEquals("vol.dufs" + separator
                + "folder1" + separator
                + "folder2" + separator
                + "file", path);
    }

    @Test
    void parsePathBeforeFile() {
        String separator = FileSystems.getDefault().getSeparator();
        String path = "vol.dufs" + separator
                + "folder1" + separator
                + "folder2" + separator
                + "file";
        String[] pathBeforeFile = Parser.parsePathBeforeFile(path);
        assertEquals(3, pathBeforeFile.length);
        assertEquals("vol.dufs", pathBeforeFile[0]);
        assertEquals("folder1", pathBeforeFile[1]);
        assertEquals("folder2", pathBeforeFile[2]);
    }

    @Test
    void parseFileNameInPath() {
        String separator = FileSystems.getDefault().getSeparator();
        String path = "vol.dufs" + separator
                + "folder1" + separator
                + "folder2" + separator
                + "file";
        String fileName = Parser.parseFileNameInPath(path);
        assertEquals("file", fileName);
    }

    @Test
    void isRecordNameOk_true() {
        String name = "okname";
        assertTrue(Parser.isRecordNameOk(name));
    }

    @Test
    void isRecordNameOk_false() {
        String name = "not<okname";
        assertFalse(Parser.isRecordNameOk(name));
    }
}