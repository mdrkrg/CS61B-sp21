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
