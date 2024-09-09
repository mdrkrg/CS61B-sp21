package gitlet;

import java.util.HashMap;
/** A Commit that is being staged
 *  Every call to gitlet add will call addToStage
 */
public class StagedCommit extends Commit {
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