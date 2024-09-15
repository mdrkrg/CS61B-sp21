package gitlet;

// NOTE: I want to replace it with java.time.ZonedDateTime

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.io.Serializable;

// TODO: Should we use a new class for merged commits? (with two parents)

/**
 * Represents a gitlet commit object.
 * <p>
 * A commit object is typically initialized with no parameter.
 * <p>
 * It's BLOBS[] should later be filled with blobs at the STAGING stage,
 * one at a time with a call to addBlob(BLOB).
 * <p>
 * When committing changes, it should be called with finalize(MESSAGE, TIME)
 *
 * @author TODO
 * @see Blob
 */
public class Commit implements GitletObject {
    /**
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    private static final Date INIT_TIMESTAMP = new Date(0);
    private static final String INIT_MESSAGE = "initial commit";
    /**
     * Parent(s) of the commit
     */
    private final Commit parent;
    /**
     * The name-blobsha1 pairs in this Commit.
     */
//    private Map<String, Blob> blobs;
    private Map<String, String> blobs;
    /**
     * The name-blob pairs to be added in this Commit.
     */
    private Map<String, Blob> added;
    /**
     * The name to be removed in this Commit.
     */
    private Set<String> removed;
    /**
     * The sha1 of this Commit.
     */
    private String sha1;
    /**
     * The message of this Commit.
     */
    private String message;
    /**
     * The timestamp of this Commit.
     */
    private Date timestamp;
    /**
     * Branch of the commit
     */
    // TODO: Branching: One branch for one commit, easier to maintain
    private String branch;
    private boolean staged;

    /**
     * Constructor only as parameters to create init commit
     */
    private Commit() {
        this.blobs = new HashMap<>();
        this.added = new HashMap<>();
        this.removed = new HashSet<>();
        this.parent = null;
    }

    /**
     * For creating Staged Commit, assume parent non-null
     */
    private Commit(Commit parent) {
        assert parent != null;
        this.blobs = parent.blobs;
        this.added = new HashMap<>();
        this.removed = new HashSet<>();
        this.parent = parent;
        this.branch = "staged";
        this.sha1 = "0000000000000000000000000000000000000000";
        this.staged = true;
    }

    /**
     * Create a new (finished) commit from a staged commit
     * Runtime: O(N) with N items in STAGED's blobs, added and removed
     *
     * @param staged    - The staged file to construct the commit
     * @param branch    - The branch that the commit belong to
     * @param message   - The commit message of the commit
     * @param timestamp - The timestamp of the commit
     */
    private Commit(Commit staged, String branch, String message, Date timestamp) {
        this.blobs = new HashMap<>(staged.blobs);
        for (String name : staged.removed) {
            this.blobs.remove(name);
        }
        for (Map.Entry<String, Blob> entry : staged.added.entrySet()) {
            this.blobs.put(entry.getKey(), entry.getValue().getSha1());
        }
        this.parent = staged.parent;
        this.branch = branch;
        this.message = message;
        this.timestamp = timestamp;
        this.sha1 = Utils.sha1(
                Utils.serialize((Serializable) this.blobs),
                // TODO: I don't like this shit
                this.parent != null
                        ? this.parent.getSha1()
                        : "0000000000000000000000000000000000000000",
                this.message,
                this.timestamp.toString()
        );
        this.staged = false;
    }

    /**
     * Create a Commit that is being staged
     * Every call to gitlet add will call addToStage
     */
    // TODO: Handle branch, create an identical parent with different message,
    //       and point the branched children to the new identical one.
    public static Commit createStagedCommit(Commit parent) {
        return new Commit(parent);
    }

    /**
     * Create a Commit that will not be changed.
     * Created on `gitlet commit`
     */
    public static Commit finishCommit(Commit staged, String branch, String message, Date timestamp) {
        return new Commit(staged, branch, message, timestamp);
    }


    /**
     * Create the initial commit:
     * - Branch:    "master"
     * - Message:   "inital commit"
     * - Timestamp: Date(0)
     *
     * @return The initial commit
     */
    public static Commit createInitCommit() {
        return Commit.finishCommit(
                new Commit(),
                Repository.DEFAULT_BRANCH,
                INIT_MESSAGE,
                INIT_TIMESTAMP
        );
    }

    /**
     * Mark one file as to be added
     * Add one blob to ADDED
     *
     * @param blob to be staged
     */
    public boolean addToStage(Blob blob) {
        assert this.staged;
        assert blob != null;
        String filename = blob.getFilename();
        Blob stagedBlob = this.added.get(filename);
        String existingSha1 = this.blobs.get(filename);
        // TODO: Class structure weird
        Blob existing = existingSha1 != null
                ? Repository.readBlobObject(existingSha1) : null;

        if (stagedBlob == null || !blob.equals(stagedBlob)) {
            // Add only when
            // 1. file not found in stage
            // 2. file not equal staged
            if (blob.equals(existing)) {
                // Remove from staged
                this.added.remove(filename);
            } else {
                this.added.put(filename, blob);
            }
            return true;
        }
        return false;
    }

    /**
     * Readd a file from REMOVED
     * @param filename - The file to be readded
     * @return true on success, false on not found in REMOVED
     */
    public boolean readdFromRemoved(String filename) {
        return this.removed.remove(filename);
    }

    /**
     * Mark a file as to be removed
     * Add a blob to REMOVED
     *
     * @return true if success, false otherwise
     */
    public boolean removeFromAll(String filename) {
        assert this.staged;
        boolean success = this.blobs.get(filename) != null;
        if (success) {
            this.removed.add(filename);
            this.added.remove(filename);
            return true;
        } else {
            return this.added.remove(filename) != null;
        }
    }

    /**
     * Mark a file to be removed from a commit
     *
     * @param filename - filename of the file to be removed
     * @return true on success, false otherwise
     */
    public boolean removeFromCommit(String filename) {
        assert this.staged;
        boolean success = this.blobs.get(filename) != null;
        if (success) {
            this.removed.add(filename);
            return true;
        }
        return false;
    }

    /**
     * Remove a file from staged
     *
     * @param filename - filename of the file
     * @return true on success, false otherwise
     */
    public boolean removeFromStage(String filename) {
        assert this.staged;
        return this.added.remove(filename) != null;
    }

    public boolean hasStagedChanges() {
        assert this.staged;
        return !this.added.isEmpty() || !this.removed.isEmpty();
    }

    public final boolean isStaged() {
        return this.staged;
    }

    public final boolean isInAdded(String filename) {
        return this.added.containsKey(filename);
    }

    public final boolean isInRemoved(String filename) {
        return this.removed.contains(filename);
    }

    public final boolean isInBlobs(String filename) {
        return this.blobs.containsKey(filename);
    }

    /**
     * Check whether a blob is different from staged
     * <p>
     * Runtime: O(1) for hashset and hashmap
     *
     * @param blob - the blob to be checked
     * @return true on different, false otherwise
     */
    public final boolean isBlobModified(Blob blob) {
        String filename = blob.getFilename();
        Blob added = this.added.get(filename);
        // first check whether file is staged
        if (added != null) {
            // file in staged
            return !added.equals(blob);
        }
        // if not found, check the last commit
        String committedSha1 = this.blobs.get(filename);
        if (committedSha1 != null) {
            // file in last commit
            return !committedSha1.equals(blob.getSha1());
        }
        return false;
    }

    /**
     * Check whether a file is new to staged
     * Assume the file exists
     *
     * @param filename - filename of the file
     * @return true on new, false otherwise
     */
    public final boolean isFileNew(String filename) {
        return (
                this.removed.contains(filename)
                        || (!this.added.containsKey(filename)
                        && !this.blobs.containsKey(filename))
        );
    }

    /**
     * Get all deleted filenames given a collection of filename
     * <p>
     * Runtime: O(N) with all N files in blobs, added and cwd
     *
     * @param files - should be the files currently in working dir
     * @return A set of deleted filenames
     */
    public final Set<String> getAllDeleted(Collection<String> files) {
        Set<String> stagedAndCommitted = new HashSet<>(this.blobs.keySet());
        stagedAndCommitted.addAll(this.added.keySet());
        stagedAndCommitted.removeAll(files);
        return stagedAndCommitted;
    }

    // This is breaking abstraction?
    public final Collection<Blob> getAddedBlobs() {
        return this.added.values();
    }

    public final Map<String, String> getAllBlobs() {
        return this.blobs;
    }

    /**
     * Given a collection of files, return a set of unstaged files,
     * with filename mapped to their reason for being unstaged.
     * <p>
     * See the design doc with pic for more detail.
     * <p>
     * Runtime: Possibly > O(N) with N files
     * <p>
     * TODO(PERF): Improve this.
     *
     * @param filesInWorkSpace - the collection of files to examine
     * @return a set of unstaged files mapped to their reason
     */
    public final SortedMap<String, Repository.UnstagedStatus> getUnstaged(Collection<String> filesInWorkSpace) {
        final Set<String> CM = this.blobs.keySet();
        final Set<String> RM = this.removed;
        final Set<String> AD = this.added.keySet();
        // O(1)
        final Set<String> FS = new HashSet<>(filesInWorkSpace);
        Set<String> all = new HashSet<>(CM);
        all.addAll(AD);
        all.removeAll(RM);
        SortedMap<String, Repository.UnstagedStatus> unstaged = new TreeMap<>();
        try {
            for (String file : FS) {
                String tmpSha1;
                Blob tmp;
                if ((tmp = this.added.get(file)) != null) {
                    // AD.contains(file)
                    Blob blob = new Blob(file);
                    if (!blob.equals(tmp)) {
                        unstaged.put(file, Repository.UnstagedStatus.MODIFIED);
                    }
                } else if ((tmpSha1 = this.blobs.get(file)) != null) {
                    // CM.contains(file)
                    Blob blob = new Blob(file);
                    if (!tmpSha1.equals(blob.getSha1())) {
                        unstaged.put(file, Repository.UnstagedStatus.MODIFIED);
                    }
                } else if (this.removed.contains(file)) {
                    // RM.contains(file)
                    unstaged.put(file, Repository.UnstagedStatus.NEW);
                } else {
                    // All other files are new
                    unstaged.put(file, Repository.UnstagedStatus.NEW);
                }
            }
            for (String fileInStage : all) {
                // This is where > O(N) can occur
                if (!FS.contains(fileInStage)) {
                    unstaged.put(fileInStage, Repository.UnstagedStatus.DELETED);
                }
            }
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
        return unstaged;
    }

    /**
     * Same logic as above, but return true once found
     * TODO: Improve algorithm
     */
    public final boolean hasUnstaged(Collection<String> filesInWorkSpace) {
        final Set<String> CM = this.blobs.keySet();
        final Set<String> RM = this.removed;
        final Set<String> AD = this.added.keySet();
        // O(1)
        final Set<String> FS = new HashSet<>(filesInWorkSpace);
        Set<String> all = new HashSet<>(CM);
        all.addAll(AD);
        all.removeAll(RM);
        try {
            for (String file : FS) {
                String tmpSha1;
                Blob tmp;
                if ((tmpSha1 = this.blobs.get(file)) != null) {
                    // CM.contains(file)
                    Blob blob = new Blob(file);
                    if (!tmpSha1.equals(blob.getSha1())) {
                        return true;
                    }
                } else if ((tmp = this.added.get(file)) != null) {
                    // AD.contains(file)
                    Blob blob = new Blob(file);
                    if (!blob.equals(tmp)) {
                        return true;
                    }
                } else if (this.removed.contains(file)) {
                    // RM.contains(file)
                    return true;
                } else {
                    // All other files are new
                    return true;
                }
            }
            for (String fileInStage : all) {
                // This is where > O(N) can occur
                if (!FS.contains(fileInStage)) {
                    return true;
                }
            }
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
        return false;
    }

    public final String getBlobSha1(final String filename) {
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

    /**
     * This is a method for testing
     */
    public final void printCommitInfo() {
        System.out.printf("commit %s\n", this.sha1);
        System.out.printf("Date: %1$ta %1$tb %1$td %1$tT %1$tY %1$tz\n", this.timestamp);
        System.out.printf("%s\n", this.message);
    }

    /**
     * This is a method for testing
     */
    public final void printBlobInfo() {
        System.out.println("Blobs: ");
        for (String bSha1 : this.blobs.values()) {
            System.out.println(bSha1);
        }
        System.out.println("Added: ");
        for (Blob b : this.added.values()) {
            System.out.println(b.getSha1());
        }
        System.out.println("Removed: ");
        for (String s : this.removed) {
            System.out.println(s);
        }
    }

    /**
     * Print the status of stage changes
     * Runtime: Possibly >O(N)
     */
    public final void printStageStatus() {
        System.out.println("=== Staged Files ===");
        List<String> addedList =
                this.added.keySet().stream().sorted()
                        .collect(Collectors.toList());
        for (String s : addedList) {
            System.out.println(s);
        }
        System.out.println();
        System.out.println("=== Removed Files ===");
        List<String> removedList =
                this.removed.stream().sorted().
                        collect(Collectors.toList());
        for (String s : removedList) {
            System.out.println(s);
        }
        System.out.println();
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