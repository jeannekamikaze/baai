package net.shellblade.bodyattack;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import ai.snips.platform.SnipsPlatformClient;

public class Snips {

    public static SnipsPlatformClient createClient(InputStream assistantZipFile, File assistantLocation)
        throws IOException {
        deployAssistant(assistantZipFile, assistantLocation);

        File assistantDir = new File(assistantLocation, "assistant");

        return new SnipsPlatformClient.Builder(assistantDir)
            .enableDialogue(true)
            .enableHotword(true)
            .enableSnipsWatchHtml(false)
            .withHotwordSensitivity(0.5f)
            .enableStreaming(false)
            .enableInjection(false)
            .enableLogs(true)
            .build();
    }

    /**
     * Deploy the assistant to a location that is accessible by the Snips platform.
     * @param assistantZipFile
     * @param assistantLocation
     * @throws IOException
     */
    private static void deployAssistant(InputStream assistantZipFile, File assistantLocation) throws IOException {
        // TODO: check the version of the assistant on disk and avoid unzipping if that same version has already been unzipped.
        //if (!assistantLocation.exists()) {
            unzip(assistantZipFile, assistantLocation);
        //}
    }

    /**
     * Unzip a zip file into the target directory.
     * @param zipFile
     * @param targetDirectory
     * @throws IOException
     */
    private static void unzip(InputStream zipFile, File targetDirectory) throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream((zipFile)));
        try {
            ZipEntry ze;
            int count;
            byte[] buffer = new byte[8192];

            while ((ze = zis.getNextEntry()) != null) {
                File file = new File(targetDirectory, ze.getName());
                File dir = ze.isDirectory() ? file : file.getParentFile();

                // Create the full path to the file.
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new FileNotFoundException("Failed to create directory: " + dir.getAbsolutePath());
                }

                // If file is really a file and not a directory, extract it.
                if (!ze.isDirectory()) {
                    // Overwrite the file if it already exists.
                    if (file.exists()) {
                        file.delete();
                    }

                    FileOutputStream fout = new FileOutputStream(file);
                    try {
                        while ((count = zis.read(buffer)) != -1) {
                            fout.write(buffer, 0, count);
                        }
                    }
                    finally {
                        fout.close();
                    }
                }
            }
        }
        finally {
            zis.close();
        }
    }
}
