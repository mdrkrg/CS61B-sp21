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
public class Commit implements Serializable {
    /**
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The name-blob pairs in this Commit. */
    private Map<String, Blob> blobs;
    /** The sha1 of this Commit. */
    private String sha1;
    /** Parent(s) of the commit */
    private Commit parent;
    /** The message of this Commit. */
    private String message;
    /** The timestamp of this Commit. */
    private Date timestamp;
    /** Branch of the commit */
    // TODO: Branching: One branch for one commit, easier to maintain
    private String branch;

    private boolean staged;

    private static final Date   INIT_TIMESTAMP = new Date(0);
    private static final String INIT_MESSAGE   = "initial commit";

    /** For creating Staged Commit */
    private Commit(Commit parent) {
        this.blobs  = new HashMap<>();
        this.parent = parent;
        this.branch = "staged";
        this.sha1   = "0000000000000000000000000000000000000000";
        this.staged = true;
    }

    private Commit(Commit staged, String branch, String message, Date timestamp) {
        this.blobs = staged.blobs;
        this.parent = staged.parent;
        this.branch = branch;
        this.message = message;
        this.timestamp = timestamp;
        this.sha1 = Utils.sha1(Utils.serialize((Serializable) this.blobs), this.message);
        this.staged = false;
    }

    /** Create a Commit that is being staged
     *  Every call to gitlet add will call addToStage
     */
    // TODO: Handle branch, create an identical parent with different message,
    //       and point the branched children to the new identical one.
    public static Commit createStagedCommit(Commit parent) {
        return new Commit(parent);
    }

    /** Create a Commit that will not be changed.
     *  Created on `gitlet commit`
     */
    public static Commit finishCommit(Commit staged, String branch, String message, Date timestamp) {
        return new Commit(staged, branch, message, timestamp);
    }


    public static Commit createInitCommit() {
        return Commit.finishCommit(new Commit(null), Repository.DEFAULT_BRANCH, INIT_MESSAGE, INIT_TIMESTAMP);
    }

    public void addToStage(String filename, Blob blob) {
        assert this.staged;
        // TODO: putIfAbsent? What if we have one in unstaged and same filename in staged?
        this.blobs.put(filename, blob);
        this.sha1 = Utils.sha1(this.blobs);
    }

    /** Remove a blob from BLOBS, by its filename.
     *  @return true if success, false otherwise
     */
    public boolean removeFromStage(String filename) {
        assert this.staged;
        boolean success = this.blobs.remove(filename) != null;
        if (success) {
            this.sha1 = Utils.sha1(this.blobs);
        }
        return success;
    }

    public final boolean isStaged() {
        return this.staged;
    }

    public final String getBranch() {
        return this.branch;
    }

    public final Commit getParent() {
        return this.parent;
    }

    public final String getMessage() {
        assert !this.staged;
        return this.message;
    }

    public final Date getTimestamp() {
        assert !this.staged;
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