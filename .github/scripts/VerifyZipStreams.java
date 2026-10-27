import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Verifies ZIP and APK archives using both seekable {@link ZipFile} and sequential
 * {@link ZipInputStream} ({@code InflaterInputStream}) decoders, including nested APKs.
 */
public final class VerifyZipStreams {
    private VerifyZipStreams() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected at least one .zip or .apk path");
        }
        for (String arg : args) {
            File file = new File(arg);
            if (!file.isFile()) {
                throw new IOException("Archive does not exist: " + file);
            }
            int zipFileEntries = verifyWithZipFile(file);
            int streamEntries = verifyWithZipInputStream(
                new BufferedInputStream(new FileInputStream(file)),
                file.getName()
            );
            if (zipFileEntries != streamEntries) {
                throw new IOException(
                    file + ": ZipFile entry count (" + zipFileEntries
                        + ") != ZipInputStream entry count (" + streamEntries + ")"
                );
            }
            if (zipFileEntries == 0) {
                throw new IOException(file + ": archive contains zero entries");
            }
            System.out.println(
                "Java ZipFile + ZipInputStream verified " + file.getName()
                    + " (" + zipFileEntries + " entries)"
            );
        }
    }

    private static int verifyWithZipFile(File file) throws IOException {
        int count = 0;
        byte[] buffer = new byte[8192];
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                count++;
                CRC32 crc = new CRC32();
                long total = 0;
                try (InputStream in = zipFile.getInputStream(entry)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        crc.update(buffer, 0, read);
                        total += read;
                    }
                }
                if (entry.getSize() >= 0 && total != entry.getSize()) {
                    throw new IOException(
                        file + ": ZipFile size mismatch for " + entry.getName()
                            + " (" + total + " != " + entry.getSize() + ")"
                    );
                }
                if (entry.getCrc() >= 0 && crc.getValue() != entry.getCrc()) {
                    throw new IOException(
                        file + ": ZipFile CRC mismatch for " + entry.getName()
                    );
                }
            }
        }
        return count;
    }

    private static int verifyWithZipInputStream(InputStream rawIn, String label) throws IOException {
        int count = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(rawIn)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                count++;
                CRC32 crc = new CRC32();
                long total = 0;
                ByteArrayOutputStream nestedApk = entry.getName().endsWith(".apk")
                    ? new ByteArrayOutputStream()
                    : null;
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    crc.update(buffer, 0, read);
                    total += read;
                    if (nestedApk != null) {
                        nestedApk.write(buffer, 0, read);
                    }
                }
                if (entry.getSize() < 0 || entry.getCrc() < 0) {
                    throw new IOException(
                        label + ": entry " + entry.getName()
                            + " is missing explicit LFH size/CRC (streaming Data Descriptor present)"
                    );
                }
                if (total != entry.getSize()) {
                    throw new IOException(
                        label + ": ZipInputStream size mismatch for " + entry.getName()
                            + " (" + total + " != " + entry.getSize() + ")"
                    );
                }
                if (crc.getValue() != entry.getCrc()) {
                    throw new IOException(
                        label + ": ZipInputStream CRC mismatch for " + entry.getName()
                    );
                }
                zis.closeEntry();
                if (nestedApk != null) {
                    int nestedCount = verifyWithZipInputStream(
                        new ByteArrayInputStream(nestedApk.toByteArray()),
                        label + "!" + entry.getName()
                    );
                    if (nestedCount == 0) {
                        throw new IOException(
                            label + "!" + entry.getName() + ": nested APK contains zero entries"
                        );
                    }
                }
            }
        }
        return count;
    }
}
