package gitlet;

// NOTE: I want to replace it with java.time.ZonedDateTime
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.io.Serializable;

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
    /** Branch of the commit */
    // TODO: Branching: One branch for one commit, easier to maintain
    protected String branch;
}

/** A Commit that is being staged
 *  Every call to gitlet add will call addToStage
 */
class StagedCommit extends Commit {
    // TODO: Branching
    protected StagedCommit(FinishedCommit parent) {
        this.blobs  = new HashMap<>();
        this.parent = parent;
        this.branch = "staged";
        this.sha1   = "0000000000000000000000000000000000000000";
    }

    public void addToStage(String filename, Blob blob) {
        // TODO: putIfAbsent? What if we have one in unstaged and same filename in staged?
        this.blobs.put(filename, blob);
        this.sha1 = Utils.sha1(this.blobs);
    }

    /** Remove a blob from BLOBS, by its filename.
     *  @return true if success, false otherwise
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
class FinishedCommit extends Commit {
    /** The message of this Commit. */
    private String message;
    /** The timestamp of this Commit. */
    private Date timestamp;

    protected FinishedCommit(Commit staged, String branch, String message, Date timestamp) {
        this.blobs = staged.blobs;
        this.parent = staged.parent;
        this.branch = branch;
        this.message = message;
        this.timestamp = timestamp;
        this.sha1 = Utils.sha1(Utils.serialize((Serializable) this.blobs));
    }

    public static FinishedCommit fromStaged(StagedCommit staged, String branch, String message, Date timestamp) {
        return new FinishedCommit(staged, branch, message, timestamp);
    }

    // TODO: Handle branch, create an identical parent with different message,
    //       and point the branched children to the new identical one.
    public StagedCommit createStage() {
        return new StagedCommit(this);
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
        return this.sha1;
    }

    @Override
    public final String toString() {
        // TODO:
        return "";
    }

    public final void printCommitMessage() {
        // TODO:
    }

}

// TODO: Improve class structure
class InitialCommit {
    private static final Date   INIT_TIMESTAMP = new Date(0);
    private static final String INIT_MESSAGE   = "initial commit";
    private static final String DEFAULT_BRANCH = "master";

    public static FinishedCommit create() {
        return FinishedCommit.fromStaged(new StagedCommit(null), DEFAULT_BRANCH, INIT_MESSAGE, INIT_TIMESTAMP);
    }
}

// class StagedMergedCommit extends StagedCommit {
//     protected FinishedCommit mergedParent;
//
//     public StagedMergedCommit(FinishedCommit mergedParent) {
//         this.mergedParent = mergedParent;
//     }
// }
//
// public class FinishedMergedCommit extends FinishedCommit {
//     protected FinishedCommit mergedParent;
//
//     public FinishedMergedCommit(StagedMergedCommit staged, String message, Date timestamp) {
//         super((StagedCommit) staged, message, timestamp);
//         this.mergedParent = staged.mergedParent;
//     }
// }