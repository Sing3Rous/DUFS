package com.dufs.filesystem;

import com.dufs.exceptions.DufsException;
import com.dufs.model.ReservedSpace;
import com.dufs.utility.VolumeIO;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class DufsFunctionalTest {
    private static File dufsFile;
    private Dufs dufs;
    private ReservedSpace reservedSpace;

    void dufsCompleteTest_dfs(File currentDirectory, String dufsSubPath, Map<String, File> files) throws IOException, DufsException {
        String[] ignoredDirectories = {".git", "target"};
        File[] records = currentDirectory.listFiles();
        String separator = FileSystems.getDefault().getSeparator();
        if (records != null) {
            for (File record : records) {
                boolean isIgnored = false;
                for (String ignoredDirectory : ignoredDirectories) {
                    if (record.getName().equals(ignoredDirectory)) {
                        isIgnored = true;
                    }
                }
                if (!isIgnored
                        && record.getName().length() <= 32 // ignore records with long name (32+ symbols)
                        && !record.getName().equals(new String(reservedSpace.getVolumeName()))) {
                    if (record.isDirectory()) {
                        dufs.createRecord(dufsSubPath, record.getName(), (byte) 0);
                        dufsCompleteTest_dfs(record, dufsSubPath + separator + record.getName(), files);
                    } else {
                        files.put(dufsSubPath + separator + record.getName(), record);
                        dufs.createRecord(dufsSubPath, record.getName(), (byte) 1);
                        dufs.writeFile(dufsSubPath + separator + record.getName(), record);
                    }
                }
            }
        }
    }

    @Test
    void dufsCompleteTest() throws IOException, DufsException {
        final boolean SHOULD_DELETE_CONTENT = true;
        String separator = FileSystems.getDefault().getSeparator();
        dufs = new Dufs();
        dufsFile = new File("cft.DUFS"); // cft -- complete functional test
        dufs.mountVolume("cft.DUFS", 4096, 40960000);
        reservedSpace = VolumeIO.readReservedSpaceFromVolume(dufs.getVolume());
        final float removeRate = 0.7F;
        File cwd = new File(new File("").getAbsolutePath());
        // key -- path in DUFS, value -- original File in external FS
        Map<String, File> files = new HashMap<>();
        // create + write all files in the volume
        dufsCompleteTest_dfs(cwd, new String(reservedSpace.getVolumeName()), files);
        List<Map.Entry<String, File>> shuffledFiles = new ArrayList<>(files.entrySet());
        Collections.shuffle(shuffledFiles);
        // delete `number of files * removeRate` files from the volume
        for (int i = 0; i < shuffledFiles.size() * removeRate; ++i) {
            dufs.deleteRecord(shuffledFiles.get(i).getKey(), (byte) 1);
        }

        dufs.defragmentation();

        // recover deleted files in new folder "./recovered"
        dufs.createRecord(new String(reservedSpace.getVolumeName()), "recovered", (byte) 0);
        String recoveredDufsPath = new String(reservedSpace.getVolumeName()) + separator + "recovered";
        for (int i = 0; i < shuffledFiles.size() * removeRate; ++i) {
            dufs.createRecord(recoveredDufsPath, shuffledFiles.get(i).getValue().getName(), (byte) 1);
            dufs.writeFile(recoveredDufsPath + separator + shuffledFiles.get(i).getValue().getName(), shuffledFiles.get(i).getValue());
        }

        dufs.closeVolume();
        dufs.attachVolume(new String(reservedSpace.getVolumeName()));

        /*
         * read all files from volume in %cwd%/dufs_content (including volume name as root dir, all directories etc.)
         * and then compare two files by all bytes -- from the volume and from the external FS
         */
        Files.createDirectory(Paths.get(cwd.getAbsolutePath() + separator + "dufs_content"));
        for (var dufsFile : files.entrySet()) {
            String realPath = (cwd.getAbsolutePath() + separator + "dufs_content" + separator + dufsFile.getKey());
            String realPathParent = realPath.substring(0, realPath.lastIndexOf(separator));
            Files.createDirectories(Paths.get(realPathParent));
            File fileFromDufs = new File(realPathParent + separator + dufsFile.getValue().getName());
            try {
                dufs.readFile(dufsFile.getKey(), fileFromDufs);
            } catch (DufsException e){  // if file was initially removed -- after recovering it contains in recovered/ subfolder
                dufs.readFile(new String(reservedSpace.getVolumeName()) + separator + "recovered"
                        + separator + dufsFile.getValue().getName(), fileFromDufs);
            }
            assertArrayEquals(Files.readAllBytes(dufsFile.getValue().toPath()), Files.readAllBytes(fileFromDufs.toPath()));
        }

        if (SHOULD_DELETE_CONTENT) {
            // delete dufs_content folder
            Files.walk(Paths.get(cwd.getAbsolutePath() + separator + "dufs_content"))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            dufs.closeVolume();
            dufsFile.delete();
        }
    }
}