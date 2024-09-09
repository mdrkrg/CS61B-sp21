package gitlet;

// NOTE: I want to replace it with java.time.ZonedDateTime
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

// TODO: Should we use a new class for merged commits? (with two parents)

/** Represents a gitlet commit object.
 *
 *  A commit object is typically initialized with no parameter.
 *
 *  It's BLOBS[] should later be filled with blobs at the STAGING stage,
 *  one at a time with a call to addBlob(BLOB).
 *
 *  When commiting changes, it should be called with finalize(MESSAGE, TIME)
 *
 *  @see Blob
 *  @author TODO
 */
public abstract class Commit implements Serializable {
    /**
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The name-blob pairs in this Commit. */
    protected Map<String, Blob> blobs;
    /** The sha1 of this Commit. */
    protected String sha1;
    /** Parent(s) of the commit */
    protected FinishedCommit parent;
}

/** A Commit that is being staged
 *  Every call to gitlet add will call addToStage
 */
public class StagedCommit extends Commit {
    public StagedCommit(FinishedCommit parent) {
        blobs = (Map) new HashMap<String, Blob>();
        this.parent = parent;
    }

    public void addToStage(String filename, Blob blob) {
        // TODO: putIfAbsent? What if we have one in unstaged and same filename in staged?
        this.blobs.put(filename, blob);
        this.sha1 = Utils.sha1(this.blobs);
    }

    /** Remove a blob from BLOBS, by its filename.
     *  @returns true if success, false otherwise
     */
    public boolean removeFromStage(String filename) {
        boolean success = this.blobs.remove(filename) != null;
        if (success) {
            this.sha1 = Utils.sha1(this.blobs);
        }
        return success;
    }
}

/** A Commit that will not be changed.
 *  Created on `gitlet commit`
 */
public class FinishedCommit extends Commit {
    /** The message of this Commit. */
    private String message;
    /** The timestamp of this Commit. */
    private Date timestamp;

    private FinishedCommit(Commit staged, String message, Date timestamp) {
        this.blobs = staged.blobs;
        this.parent = staged.parent;
        this.message = message;
        this.timestamp = timestamp;
        this.sha1 = Utils.sha1(this.message, this.timestamp, this.blobs);
    }

    public static fromStaged(StagedCommit staged, String message, Date timestamp) {
        return new FinishedCommit((Commit) staged, message, timestamp);
    }

    public void setCommitMessage(String message) {
        this.message = message;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public final String getMessage()  {
        return this.message;
    }

    public final Date getTimestamp() {
        return this.timestamp;
    }

    public final String getSha1() {
        return this.Sha1;
    }

    @Override
    public final String toString() {
        // TODO:
    }

    public final void printCommitMessage() {
        // TODO:
    }
}

public class StagedMergedCommit extends StagedCommit {
    protected FinishedCommit mergedParent;

    public StagedMergedCommit(FinishedCommit mergedParent) {
        this.mergedParent = mergedParent;
    }
}

public class FinishedMergedCommit extends FinishedCommit {
    protected FinishedCommit mergedParent;

    public FinishedMergedCommit(StagedMergedCommit staged, String message, Date timestamp) {
        super((StagedCommit) staged, message, timestamp);
        this.mergedParent = staged.mergedParent;
    }
}
