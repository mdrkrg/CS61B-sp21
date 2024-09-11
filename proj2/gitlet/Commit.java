package gitlet;

// NOTE: I want to replace it with java.time.ZonedDateTime
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.io.Serializable;

// TODO: Should we use a new class for merged commits? (with two parents)

/** Represents a gitlet commit object.
 *
 *  A commit object is typically initialized with no parameter.
 *
 *  It's BLOBS[] should later be filled with blobs at the STAGING stage,
 *  one at a time with a call to addBlob(BLOB).
 *
 *  When committing changes, it should be called with finalize(MESSAGE, TIME)
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
    /** The name-blob pairs to be added in this Commit. */
    private Map<String, Blob> added;
    /** The name to be removed in this Commit. */
    private Set<String> removed;
    /** The sha1 of this Commit. */
    private String sha1;
    /** Parent(s) of the commit */
    private final Commit parent;
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

    /** Constructor only as parameters to create init commit */
    private Commit() {
        this.blobs  = new HashMap<>();
        this.added   = new HashMap<>();
        this.removed = new HashSet<>();
        this.parent = null;
    }

    /** For creating Staged Commit, assume parent non-null */
    private Commit(Commit parent) {
        assert parent != null;
        this.blobs   = parent.blobs;
        this.added   = new HashMap<>();
        this.removed = new HashSet<>();
        this.parent  = parent;
        this.branch  = "staged";
        this.sha1    = "0000000000000000000000000000000000000000";
        this.staged  = true;
    }

    private Commit(Commit staged, String branch, String message, Date timestamp) {
        this.blobs = new HashMap<>(staged.blobs);
        for (String name: staged.removed) {
            this.blobs.remove(name);
        }
        this.blobs.putAll(staged.added);
        staged.added = null;
        staged.removed = null;
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
        return Commit.finishCommit(new Commit(), Repository.DEFAULT_BRANCH, INIT_MESSAGE, INIT_TIMESTAMP);
    }

    /** Mark one file as to be added
     *  Add one blob to ADDED
     *
     *  @param blob to be staged
     */
    public boolean addToStage(Blob blob) {
        assert this.staged;
        assert blob != null;
        String filename = blob.getFilename();
        Blob stagedBlob = this.added.get(filename);

        if (stagedBlob == null || !blob.equals(stagedBlob)) {
            // Add only when
            // 1. file not found in stage
            // 2. file not equal staged
            if (blob.equals(this.blobs.get(filename))) {
                // Remove from staged
                this.added.remove(filename);
            } else {
                this.added.put(filename, blob);
            }
            return true;
        }
        return false;
        // TODO: putIfAbsent? What if we have one in unstaged and same filename in staged?
        // this.sha1 = Utils.sha1(this.blobs);
    }

    /** Mark a file as to be removed
     *  Add a blob to REMOVED
     *  @return true if success, false otherwise
     */
    public boolean removeFromStage(String filename) {
        assert this.staged;
        boolean success = this.blobs.get(filename) != null;
        if (success) {
            this.removed.add(filename);
            return true;
        }
        success = this.added.get(filename) != null;
        if (success) {
            this.added.remove(filename);
            return true;
        }
        return false;
    }

    public final boolean isStaged() {
        return this.staged;
    }

    public final Blob getBlob(final String filename) {
        return this.blobs.get(filename);
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

    /** This is a method for testing */
    public final void printCommitInfo() {
        System.out.printf("Commit %s:\n", this.sha1);
        System.out.printf("Message:\t%s\n", this.message);
        System.out.printf("Timestamp:\t%s\n", this.timestamp);
        System.out.printf("Branch:\t%s\n", this.branch);
    }

    public final void printBlobInfo() {
        System.out.println("Blobs: ");
        for (Blob b: this.blobs.values()) {
            System.out.println(b.getSha1());
        }
        System.out.println("Added: ");
        for (Blob b: this.added.values()) {
            System.out.println(b.getSha1());
        }
        System.out.println("Removed: ");
        for (String s: this.removed) {
            System.out.println(s);
        }
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