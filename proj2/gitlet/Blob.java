package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;

/** A data structure containing a file's version and information.
 *
 */
public class Blob implements Serializable {
    /* The file describer of the blob */
    private File file;
    /* The file name of the blob */
    private String filename;
    /* The checksum of the blob */
    private String sha1;

    /** Create a blob with given file name
     *
     *  @param  filename - The file name of the file
     */
    public Blob(String filename) throws GitletException, IOException {
        File f = new File(filename);
        if (!f.exists()) {
            // TODO: Read the spec for error message
            throw new GitletException("File doesn't exists!");
        }
        this.file = f;
        this.filename = filename;
        this.sha1 = Utils.sha1(file.length(), " ", Files.readAllBytes(file.toPath()));
    }

    /** Check whether two blobs are equal
     *  @param other - The blob to compare
     */
    public boolean equals(Blob other) {
        return this.sha1.equals(other.sha1);
    }
}