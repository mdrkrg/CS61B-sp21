package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * A data structure containing a file's version and information.
 */
public class Blob implements GitletObject {

    /* The file describer of the blob */
    private final File file;
    /* The file name of the blob */
    private final String filename;
    /* The checksum of the blob */
    private final String sha1;
    /* The content of the file */
    private final byte[] data;

    /**
     * Create a blob with given file name
     *
     * @param filename - The file name of the file
     */
    public Blob(String filename) throws GitletException, IOException {
        File f = new File(filename);
        if (!f.exists()) {
            throw new GitletException("File does not exist.");
        }
        this.file = f;
        this.filename = filename;
        this.data = Files.readAllBytes(f.toPath());
        this.sha1 = Utils.sha1(Utils.serialize(Files.size(f.toPath())), this.data, this.filename);
    }

    /**
     * Check whether two blobs are equal
     * Runtime: O(1)
     *
     * @param other - The blob to compare
     */
    public boolean equals(Blob other) {
        if (other == null) {
            return false;
        }
        return this.sha1.equals(other.sha1);
    }

    public int hashCode() {
        return sha1.hashCode();
    }

    @Override
    public final String toString() {
        return this.filename + " - " + this.sha1;
    }

    public final String getSha1() {
        return this.sha1;
    }

    public final String getFilename() {
        return this.filename;
    }

    public final byte[] getData() {
        return this.data;
    }

    public final File getFile() {
        return this.file;
    }
}
