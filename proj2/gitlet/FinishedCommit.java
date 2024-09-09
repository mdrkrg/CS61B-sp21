package gitlet;

import java.io.Serializable;
import java.util.Date;
/** A Commit that will not be changed.
 *  Created on `gitlet commit`
 */
public class FinishedCommit extends Commit {
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