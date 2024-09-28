package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static gitlet.Utils.*;

/**
 * Represents a gitlet repository.
 * Contains static methods for manipulating the files
 *
 * @author Crvena
 */
public class Repository {
    /**
     * The current working directory.
     */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    // public static final File BRANCHES_DIR      = join(GITLET_DIR, "branches");
    public static final File STAGE_FILE = join(GITLET_DIR, "stage");
    public static final File LOGS_DIR = join(GITLET_DIR, "logs");
    public static final File LOGS_HEAD_FILE = join(GITLET_DIR, "logs", "HEAD");
    public static final File LOGS_REFS_DIR = join(GITLET_DIR, "logs", "refs");
    public static final File LOGS_REFS_HEADS_DIR = join(GITLET_DIR, "logs", "refs", "heads");
    public static final File REMOVED_LOG = Utils.join(GITLET_DIR, "logs", "refs", "removed");
    public static final File ROOT_HEAD_FILE = join(GITLET_DIR, "HEAD");
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    public static final File REFS_DIR = join(GITLET_DIR, "refs");
    public static final File REFS_HEADS_DIR = join(GITLET_DIR, "refs", "heads");

    public static final String DEFAULT_BRANCH = "master";

    /**
     * Init the gitlet Repository
     * Assume that the repository has not yet been inited.
     */
    static void init() {
        Commit initCommit;
        try {
            makeEssentialDir();
            createInitFile();
            initCommit = makeInitCommit();
            updateRootHead(initCommit.getBranch());
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    /**
     * Add a file to stage.
     * <p>
     * There're five cases in total that can occur:
     * 1. File not changed : filename equals, sha1 equals
     * 2. File changed     : filename equals, sha1 unequals
     * 3. File renamed     : filename unequals, sha1 equals (how?) (two ops)
     * - If you use an iterator to iterate through all entries in
     * the blobs and check if sha1 equals, that will be O(n) time
     * - Git uses two lists: One deleted, one Added, how to implement?
     * 4. File is new      : filename doesn't exist
     * 5. File deleted     : filename exists, !file.exists()
     * 6. File changed and renamed??? -> separate to two
     * 7. File removed but added again
     * <p>
     * // WARN: If switch a branch with staged changes,
     * the target branch may contain same file as the staged
     *
     * @param filename file to be staged.
     */
    static void add(final String filename) {
        Commit staged = getStagedCommit();
        if (staged.readdFromRemoved(filename)) {
            // File already in REMOVED
            // Should restore the file (required)
            String removedSha1 = staged.getBlobSha1(filename);
            restoreBlobContent(removedSha1);
            writeStageFile(staged);
        } else try {
            Blob blob = new Blob(filename);
            boolean addSuccessful = staged.addToStage(blob);
            if (addSuccessful) {
                writeStageFile(staged);
            }
        } catch (GitletException e) {
            // File not exist in workspace
            // Case 5 or error
            boolean removeSuccessful = staged.removeFromAll(filename);
            if (!removeSuccessful) {
                // WARN: This is unsure whether to implement this behaviour
                //       Need to refer to spec if failed.
                throw new GitletException("File does not exist.");
            }
            writeStageFile(staged);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    /**
     * Remove a file from staged
     * If the file is exisiting in workspace, rm it
     * Three case:
     * 1. File is in ADDED (staged)
     * 2. File is in BLOBS (committed)
     * <p>
     * Operations:
     * 1. Remove the file from ADDED
     * 2. Add the file to REMOVED, delete the file
     *
     * @param filename - file to remove from stage
     */
    static void remove(final String filename) {
        Commit staged = getStagedCommit();
        boolean stageRemoveSuccess = staged.removeFromStage(filename);
        boolean blobsRemoveSuccess = false;
        if (!stageRemoveSuccess) {
            blobsRemoveSuccess = staged.removeFromCommit(filename);
        }

        // Hashmap constant time
        if (blobsRemoveSuccess) {
            File file = new File(filename);
            if (file.exists()) {
                file.delete();
            }
        }
        if (!stageRemoveSuccess && !blobsRemoveSuccess) {
            throw new GitletException("No reason to remove the file.");
        } else {
            writeStageFile(staged);
        }
    }

    /**
     * Commit a new snapshot from staged changes
     * 1. Save the blobs in the added
     * 2. Save the commit object
     * 3. Delete the stage file
     * <p>
     * Runtime: >O(N) (hasStagedChanges())
     *
     * @param message - User inputted commit message
     * @throws GitletException - When no staged changes or no message
     */
    static void commit(final String message) throws GitletException {
        Commit staged = getStagedCommit();
        final String branch = getCurrentBranch();
        if (!staged.hasStagedChanges()) {
            throw new GitletException("No changes added to the commit.");
        }
        if (message.isBlank()) {
            throw new GitletException("Please enter a commit message.");
        }
        try {
            Commit newCommit = Commit.finishCommit(staged, branch, message, new Date());
            for (Blob b : staged.getAddedBlobs()) {
                try {
                    writeBlobObject(b);
                    // Ignore hash collision
                } catch (GitletException e) {}
            }
            writeCommitFiles(newCommit);
            clearStageFile();
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    /**
     * Print the log of current branch
     * Runtime: O(N) with N commits in current branch
     */
    static void log() {
        Commit head = getHeadCommit();
        while (head != null) {
            System.out.println("===");
            head.printCommitInfo();
            System.out.println();
            head = head.getParent();
        }
    }

    /**
     * Print the global log of all commits, regardless of the order
     * Runtime: O(N) with N total commits
     */
    static void globalLog() {
        List<String> logs = Utils.plainFilenamesIn(LOGS_REFS_HEADS_DIR);
        assert logs != null;
        try {
            for (String filename: logs) {
                File logFile = Utils.join(LOGS_REFS_HEADS_DIR, filename);
                String[] lines = Utils.readContentsAsString(logFile).split("\n");
                for (int i = lines.length - 1; i >= 0; i--) {
                    printLogLineInfo(lines[i]);
                }
            }
            if (REMOVED_LOG.exists()) {
                String[] removedLines = Utils.readContentsAsString(REMOVED_LOG).split("\n");
                for (int i = removedLines.length - 1; i >= 0; i--) {
                    printLogLineInfo(removedLines[i]);
                }
            }
        } catch (IllegalArgumentException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    /**
     * Print a log string given a log line of text with the following format:
     * "[parent commit] [current commit] [timestamp] [commit message]"
     * Runtime: O(1)
     * @param logFileLine - A line of text in the file logs/refs/heads/[branch]
     */
    private static void printLogLineInfo(String logFileLine) {
        String[] tokens = logFileLine.split(" ", 4);
        String commitSha1 = tokens[1];
        Date timestamp = new Date(Long.parseLong(tokens[2]));
        String commitMsg = tokens[3];
        System.out.println("===");
        System.out.printf(
                "commit %1$s\nDate: %2$ta %2$tb %2$td %2$tT %2$tY %2$tz\n%3$s\n",
                commitSha1,
                timestamp,
                commitMsg
        );
        System.out.println();
    }

    /**
     * Print the commit IDs that have the same commit message as [queryMsg],
     * seperated by linebreaks.
     * Runtime: O(N) with N total commits
     * @param queryMsg - The message to find
     * @throws GitletException - When no such commit with the same message exists
     */
    static void find(String queryMsg) throws GitletException {
        List<String> logs = Utils.plainFilenamesIn(LOGS_REFS_HEADS_DIR);
        boolean found = false;
        assert logs != null;
        try {
            for (String filename: logs) {
                File logFile = Utils.join(LOGS_REFS_HEADS_DIR, filename);
                // If findOneFile returns true, found will always come true
                found = found || findOneFile(logFile, queryMsg);
            }
            if (REMOVED_LOG.exists()) {
                // Find the removed log file;
                found = found || findOneFile(REMOVED_LOG, queryMsg);
            }
        } catch (IllegalArgumentException e) {
            ErrorHandler.handleJavaException(e);
        }
        if (!found) {
            throw new GitletException("Found no commit with that message.");
        }
    }

    private static boolean findOneFile(File logFile, String queryMsg) {
        boolean found = false;
        String[] lines = Utils.readContentsAsString(logFile).split("\n");
        for (String line: lines) {
            String[] tokens = line.split(" ", 4);
            String commitID = tokens[1];
            String commitMsg = tokens[3];
            if (queryMsg.equals(commitMsg)) {
                System.out.println(commitID);
                found = true;
            }
        }
        return found;
    }


    static void createNewBranch(String name) throws GitletException {
        final File BRANCH_FILE = Utils.join(REFS_HEADS_DIR, name);
        if (BRANCH_FILE.exists()) {
            throw new GitletException("A branch with that name already exists.");
        }
        try {
            BRANCH_FILE.createNewFile();
            String headSha1 = getHeadCommit().getSha1();
            Utils.writeContents(BRANCH_FILE, headSha1);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    static void removeBranch(String branch) throws GitletException {
        final File BRANCH_FILE = Utils.join(REFS_HEADS_DIR, branch);
        if (!BRANCH_FILE.exists()) {
            throw new GitletException("A branch with that name does not exist.");
        }
        String current = getCurrentBranch();
        if (branch.equals(current)) {
            throw new GitletException("Cannot remove the current branch.");
        }
        final File BRANCH_LOG = Utils.join(LOGS_REFS_HEADS_DIR, branch);
        try {
            if (!REMOVED_LOG.exists()) {
                REMOVED_LOG.createNewFile();
            }
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
        String oldContent = Utils.readContentsAsString(REMOVED_LOG);
        String appendContent = Utils.readContentsAsString(BRANCH_LOG);
        Utils.writeContents(REMOVED_LOG, oldContent, appendContent);
        BRANCH_FILE.deleteOnExit();
        BRANCH_LOG.deleteOnExit();
    }

    static void switchToBranch(String name) throws GitletException {
        String currentBranch = getCurrentBranch();
        if (name.equals(currentBranch)) {
            throw new GitletException("No need to checkout the current branch.");
        }

        final File BRANCH_FILE = Utils.join(REFS_HEADS_DIR, name);
        if (!BRANCH_FILE.exists()) {
            throw new GitletException("No such branch exists.");
        }

//        if (hasUnstagedChanges()) {
//            throw new GitletException(
//                    "There is an untracked file in the way; delete it, or add and commit it first."
//            );
//        }
        List<Blob> snapshot = snapshotWorkspace();
        try {
            Commit branchHead = getHeadCommit(name);
            restoreToCommit(branchHead);
            updateRootHead(name);
            updateStageFileTo(name);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        } catch (GitletException e) {
            restoreWorkspace(snapshot);
            throw e;
        }
    }

    static void reset(String commitID) throws GitletException {
        // Throws "no commit exist" if not found
        Commit commit = getCommit(commitID);
        if (hasUnstagedChanges()) {
            // If a working file is untracked in the current branch
            //        and WOULD BE OVERWRITTEN by the reset
            throw new GitletException(
                    "There is an untracked file in the way; delete it, or add and commit it first."
            );
        }
        List<Blob> snapshot = snapshotWorkspace();
        try{
            writeCommitRef(getCurrentBranch(), commit);
            // For log, simply don't update them
            restoreToCommit(commit);
            clearStageFile();
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        } catch (GitletException e) {
            Commit currentCommit = getHeadCommit();
            restoreWorkspace(snapshot);
            throw e;
        }
    }

    private static GitletException UnstagedChangesException() {
        return new GitletException("There is an untracked file in the way; delete it, or add and commit it first.");
    }

    /**
     * Merge a target branch into current branch
     * @param target The branch to merge into
     * @throws GitletException See the specs
     */
    static void merge(String target) throws GitletException {
        Commit staged = getStagedCommit();
        if (staged.hasStagedChanges()) {
            throw new GitletException("You have uncommitted changes.");
        }

        // If an untracked file in the current commit would be overwritten or deleted by the merge
//        if (hasUnstagedChanges()) {
//            throw new GitletException("There is an untracked file in the way; delete it, or add and commit it first.");
//        }

        String currentBranch = getCurrentBranch();
        if (currentBranch.equals(target)) {
            throw new GitletException("Cannot merge a branch with itself.");
        }

        final File BRANCH_FILE = Utils.join(REFS_HEADS_DIR, target);
        if (!BRANCH_FILE.exists()) {
            throw new GitletException("A branch with that name does not exist.");
        }

        Commit headCommit = getHeadCommit();
        Commit targetCommit = getHeadCommit(target);
        Commit commonAncestor = getCommonAncestor(currentBranch, target);

        if (commonAncestor.equals(targetCommit)) {
            throw new GitletException("Given branch is an ancestor of the current branch.");
        }

        // Fast-forward
        if (commonAncestor.equals(headCommit)) {
            try {
                restoreToCommit(targetCommit);
                writeCommitRef(currentBranch, targetCommit);
                System.out.println("Current branch fast-forwarded.");
                return;
            } catch (IOException e) {
                ErrorHandler.handleJavaException(e);
            }
        }
        clearStageFile();

        // NOTE: staged stems from current branch
        staged = getStagedCommit();

        Map<String, String> splitBlobs = commonAncestor.getAllBlobs();
        Map<String, String> thisBlobs = headCommit.getAllBlobs();
        Map<String, String> targetBlobs = targetCommit.getAllBlobs();

        HashSet<String> checkedFiles = new HashSet<>();

        HashSet<String> cwdFiles = getCWDFiles();

        List<Blob> snapshot = snapshotWorkspace();

        // Now, the filename is part of blob sha1, so this can be some problem
        try {
            for (Map.Entry<String, String> entry : splitBlobs.entrySet()) {
                String splitBlobSha1 = entry.getValue();
                String splitFilename = entry.getKey();

                checkedFiles.add(splitFilename);
                Blob splitBlob = readBlobObject(entry.getValue());

                String targetBlobSha1 = targetBlobs.get(splitFilename);
                String thisBlobSha1 = thisBlobs.get(splitFilename);

                int status = getMergeStatus(splitBlobSha1, targetBlobSha1, thisBlobSha1);

                switch (status) {
                    case 1 -> {
                        // modified in the given branch,
                        // but not modified in the current branch
                        Blob targetBlob = readBlobObject(targetBlobSha1);
                        testUnstaged(splitFilename, splitBlob, cwdFiles);
                        // files should be checkouted and staged
                        restoreBlobContent(targetBlob);
                        staged.addToStage(targetBlob);
                    }
                    case 2, 3, 7 -> {
                        // 2: modified in the current branch
                        // but not in the given branch
                        // 3: both modified the same way
                        // or both deleted
                        // 7: present at the split point,
                        // unmodified in the given branch,
                        // and absent in the current branch
                    }
                    case 6 -> {
                        // present at the split point,
                        // unmodified in the current branch,
                        // and absent in the given branch
                        testUnstaged(splitFilename, splitBlob, cwdFiles);
                        // should check unstaged first
                        // should be removed (and untracked)
                        staged.removeFromCommit(splitFilename);
                        File file = new File(splitFilename);
                        file.delete();
                    }
                    case 8 -> {
                        Blob thisBlob = readBlobObject(thisBlobSha1);
                        testUnstaged(splitFilename, thisBlob, cwdFiles);
                        // Write the diff file
                        // TODO: Improve this
                        File tmp = markDiff(splitFilename, thisBlobSha1, targetBlobSha1);
                        Utils.writeContents(tmp, (Object) thisBlob.getData());
                    }
                }
            }

            // case 5
            for (Map.Entry<String, String> entry : targetBlobs.entrySet()) {
                String targetFilename = entry.getKey();
                String targetBlobSha1 = entry.getValue();
                // not present at the split point
                // are present only in the given branch
                if (!checkedFiles.contains(targetFilename)
                        && !thisBlobs.containsKey(targetFilename)) {
                    checkedFiles.add(targetFilename);
                    Blob targetBlob = readBlobObject(targetBlobSha1);
                    // files should be checkouted and staged
                    testUnstaged(targetFilename, targetBlob, cwdFiles);
                    restoreBlobContent(targetBlob);
                    staged.addToStage(targetBlob);
                }
            }
        } catch (GitletException e) {
            // When bad things happen, restore stuff
            restoreRefHead(currentBranch, headCommit);
            restoreWorkspace(snapshot);
            throw e;
        }


        // not present at the split point
        // are present only in the current branch
        // Nop
        try {
            // Move all tmp files to cwd
            moveTmp();

            // If merge would generate an error because the commit that it does has no changes in it,
            // just let the normal commit error message for this go through.
            String commitMessage = String.format("Merged %s into %s.", target, currentBranch);
            commitMerge(staged, targetCommit, commitMessage);
            removeTmp();
        } catch (IOException e) {
            restoreRefHead(currentBranch, headCommit);
            restoreWorkspace(snapshot);
            ErrorHandler.handleJavaException(e);
        }
    }

    /**
     * Commit the merge
     * @param staged The staged commit
     * @param targetCommit The commit to merge into
     * @param message The commit message
     */
    private static void commitMerge(Commit staged, Commit targetCommit, String message) {
        final String branch = getCurrentBranch();
        if (!staged.hasStagedChanges()) {
            throw new GitletException("No changes added to the commit.");
        }
        try {
            Commit newCommit = Commit.finishCommit(staged, branch, message, new Date(), targetCommit);
            for (Blob b : staged.getAddedBlobs()) {
                try {
                    writeBlobObject(b);
                } catch (GitletException e) {}
            }
            writeCommitFiles(newCommit);
            clearStageFile();
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    /**
     * Get the status code of one file in the split commit
     * @param splitSha1 Sha1 of the blob in split commit
     * @param targetSha1 Sha1 of the blob in target commit
     * @param thisSha1 Sha1 of the blob in this commit
     * @return Status code
     */
    private static int getMergeStatus(String splitSha1, String targetSha1, String thisSha1) {
        assert splitSha1 != null;
        if (targetSha1 == null && thisSha1 == null) {
            // Both deleted
            return 3;
        }

        if (targetSha1 == null && thisSha1.equals(splitSha1)) {
            // present at the split point,
            // unmodified in the current branch,
            // and absent in the given branch
            return 6;
            // should check unstaged first
            // should be removed (and untracked)
        }

        if (thisSha1 == null && targetSha1.equals(splitSha1)) {
            // present at the split point,
            // unmodified in the given branch,
            // and absent in the current branch
            return 7;
            // should remain absent
        }

        if (targetSha1 != null && thisSha1 != null && targetSha1.equals(thisSha1)) {
            // both modified the same way
            return 3;
            // should left unchanged
        }

        if (thisSha1 == null && targetSha1 != null && !targetSha1.equals(splitSha1)) {
            // one is changed, other is modified
            return 8;
        }

        if (targetSha1 == null && thisSha1 != null && !thisSha1.equals(splitSha1)) {
            // one is changed, other is modified
            return 8;
        }

        if (thisSha1.equals(splitSha1) && !targetSha1.equals(splitSha1)) {
            // modified in the given branch,
            // but not modified in the current branch
            return 1;
            // files should be checkouted and staged
        }

        if (!thisSha1.equals(splitSha1) && targetSha1.equals(splitSha1)) {
            // modified in the current branch
            // but not in the given branch
            return 2;
            // file should stay
        }
        if (targetSha1 != null && thisSha1 != null && !targetSha1.equals(thisSha1)) {
            // modified differently
            return 8;
        }

        // Other conditions
        return 0;
    }

    private static final File tmpDir = Utils.join(GITLET_DIR, "tmp");

    /**
     * File in CWD should have no unstaged changes
     * @param filename
     * @param ourSha1
     * @param theirSha1
     * @return A tmpFile contains the diff result, should be deleted
     */
    private static File markDiff(String filename, String ourSha1, String theirSha1) {
        if (!tmpDir.exists()) {
            tmpDir.mkdir();
        }
        final File tmpFile = Utils.join(tmpDir, filename);
        tmpFile.delete();
        Blob our = readBlobObject(ourSha1);
        Blob their = readBlobObject(ourSha1);
        try {
            tmpFile.createNewFile();
            return tmpFile;
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
        throw new AssertionError("never reached");
    }

    /**
     * Remove tmp files generated by merge conflicts
     */
    static void removeTmp() {
        if (!tmpDir.exists()) {
            return;
        }
        List<String> tmpFiles = Utils.plainFilenamesIn(tmpDir);
        if (tmpFiles == null) {
            return;
        }
        for (String filename: tmpFiles) {
            File file = Utils.join(tmpDir, filename);
            file.delete();
        }
    }

    /**
     * Move tmp file generated by merge conflict into CWD
     * @throws IOException - When IO System fails
     */
    private static void moveTmp() throws IOException {
        if (!tmpDir.exists()) {
            return;
        }
        List<String> tmpFiles = Utils.plainFilenamesIn(tmpDir);
        if (tmpFiles == null) {
            return;
        }
        for (String filename: tmpFiles) {
            File tmp = Utils.join(tmpDir, filename);
            File cur = Utils.join(CWD, filename);
            Files.move(tmp.toPath(), cur.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        removeTmp();
    }

    /**
     * Restore the refs/heads/BRANCH file to COMMIT
     * @param branch The branch to restore
     * @param commit The commit to restore the branch head to
     */
    static void restoreRefHead(String branch, Commit commit) {
        try {
            writeCommitRef(branch, commit);
        } catch (IOException e) {
//            restoreRefHead(branch, commit);
            ErrorHandler.handleJavaException(e);
        }
    }

    /**
     * Make a snapshot of current working space for future restore
     * @return A List of Blobs containing all files in the CWD
     */
    private static List<Blob> snapshotWorkspace() {
        List<Blob> snapshot = new ArrayList<>();
        HashSet<String> files = getCWDFiles();
        try {
            for (String file: files) {
                Blob blob = new Blob(file);
                snapshot.add(blob);
            }
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
        return snapshot;
    }

    /**
     * Restore a Gitlet workspace given the head commit
     * @param snapshot A list of blobs in the cwd
     */
    static void restoreWorkspace(List<Blob> snapshot) {
        for (Blob blob: snapshot) {
            restoreBlobContent(blob);
        }
        removeTmp();
        clearStageFile();
    }

    /**
     * Given a filename, if it's in CWD
     * test whether the blob in CWD is different from OTHER
     * If true, throw UnstagedChangesException
     * @param filename Filename to test
     * @param other The blob to compare
     */
    private static void testUnstaged(String filename, Blob other, HashSet<String> cwdFiles) throws GitletException {
        // Check if the file is unstaged modified
        if (cwdFiles.contains(filename)) try {
            Blob cwdBlob = new Blob(filename);
            if (!cwdBlob.equals(other)) {
                throw UnstagedChangesException();
            }
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }


    /**
     * Find the common ancestor given one branch and another
     * Algorithm: BFS
     * Runtime: O(V+E) for ours V vertexes, E edges
     * @param our - Name of our branch
     * @param their - Name of their branch
     * @return The latest common ancestor
     */
    public static Commit getCommonAncestor(String our, String their) {
        Queue<Commit> nodeQueue = new ArrayDeque<>();
        HashSet<Commit> ourVisited = new HashSet<>();
        HashSet<Commit> theirAncestors = getAllAncestors(their);
        Commit ourHead = getHeadCommit(our);
        nodeQueue.add(ourHead);
        ourVisited.add(ourHead);
        // BFS to return common ancestor
        while (!nodeQueue.isEmpty()) {
            Commit current = nodeQueue.remove();
            if (theirAncestors.contains(current)) {
                // If found, immediately return
                return current;
            }
            // else search node's parents
            Commit left = current.getParent();
            Commit right = current.getMergedParent();
            if (left == null) {
                if (right != null) {
                    throw new AssertionError("Shouldn't happen");
                }
            } else {
                if (!ourVisited.contains(left)) {
                    nodeQueue.add(left);
                    ourVisited.add(left);
                }
            }
            if (right != null) {
                if (!ourVisited.contains(right)) {
                    nodeQueue.add(right);
                    ourVisited.add(right);
                }
            }
        }
        throw new AssertionError("Shouldn't happen");
    }

    /**
     * Get all ancestors of a given branch
     * Algorithm: DFS
     * Runtime: O(V+E) for the given branch's V vertexes, E edges
     * @param branch The branch to get
     * @return A set of ancestor commits
     */
    private static HashSet<Commit> getAllAncestors(String branch) {
        Commit head = getHeadCommit(branch);
        Stack<Commit> commitStack = new Stack<>();
        HashSet<Commit> visited = new HashSet<>();
        commitStack.push(head);
        // DFS to fill the ALL HashSet
        while(!commitStack.isEmpty()) {
            Commit current = commitStack.pop();
            if (!visited.contains(current)) {
                visited.add(current);
                Commit left = current.getParent();
                Commit right = current.getMergedParent();
                if (left == null) {
                    if (right != null) {
                        throw new AssertionError("Shouldn't happen");
                    }
                } else if (right == null) {
                    commitStack.push(left);
                } else {
                    commitStack.push(left);
                    commitStack.push(right);
                }
            }
        }
        return visited;
    }

    static void printStatus() {
        printBranches();
        Commit staged = getStagedCommit();
        staged.printStageStatus();
        printUnstagedChanges();
    }

    static private void printUnstagedChanges() {
        SortedMap<String, UnstagedStatus> unstaged = getUnstagedFiles();
        List<String> newFiles = new ArrayList<>();

        System.out.println("=== Modifications Not Staged For Commit ===");
        for (Map.Entry<String, UnstagedStatus> entry : unstaged.entrySet()) {
            String filename = entry.getKey();
            switch (entry.getValue()) {
                case NEW -> newFiles.add(filename);
                case MODIFIED -> System.out.printf("%s (modified)\n", filename);
                case DELETED -> System.out.printf("%s (deleted)\n", filename);
            }
        }
        System.out.println();
        System.out.println("=== Untracked Files ===");
        for (String filename : newFiles) {
            System.out.println(filename);
        }
        System.out.println();
    }

    static void printBranches() {
        System.out.println("=== Branches ===");
        String currentBranch = getCurrentBranch();
        System.out.printf("*%s\n", currentBranch);
        List<String> branches = getBranches();
        assert branches != null;
        for (String branch : branches) {
            if (!branch.equals(currentBranch)) {
                System.out.println(branch);
            }
        }
        System.out.println();
    }

    /**
     * Get a list of all branches in the current gitlet directory
     * The branches are the filenames in the directory: refs/heads/*
     * <p>
     * Runtime: O(N) with N branches (N files in directory)
     *
     * @return A list of all branches
     * @throws GitletException - When the refs/heads/ doesn't exist
     */
    private static List<String> getBranches() throws GitletException {
        if (!REFS_HEADS_DIR.exists()) {
            throw new GitletException("Broken Gitlet Repository!");
        }
        return Utils.plainFilenamesIn(REFS_HEADS_DIR);
    }

    /**
     * Get the current branch of the gitlet repository
     * Current branch is in the HEAD plain text file,
     * in the format of "refs/heads/[BRANCH]"
     * Runtime: O(1)
     *
     * @return The current branch of the gitlet working directory
     * @throws GitletException - When head file doesn't exist
     */
    public static String getCurrentBranch() throws GitletException {
        if (!ROOT_HEAD_FILE.exists()) {
            throw new GitletException("Broken gitlet repository: .gitlet/HEAD not found!");
        }
        final String rootContent = Utils.readContentsAsString(ROOT_HEAD_FILE);
        // final String[] tokens = rootContent.split("/");
        // assert tokens.length == 3;
        return rootContent.substring(rootContent.lastIndexOf("/") + 1);
    }

    /**
     * Get a commit of the given commit ID (assume at least 5 char long)
     * Runtime: ~O(1) with N total commits (hashset file structure)
     * @param commitID - The sha1 identifier of a commit
     * @return Commit object
     * @throws GitletException - When commit of the given ID doesn't exist
     */
    public static Commit getCommit(final String commitID) throws GitletException {
        final String ERROR_MSG = "No commit with that id exists.";
        final String commitDirname = commitID.substring(0, 2);
        String commitFilename = commitID.substring(2);
        if (commitID.length() != 40) {
            final File COMMIT_OBJECT_DIR = Utils.join(OBJECTS_DIR, commitID.substring(0, 2));
            if (!COMMIT_OBJECT_DIR.exists()) {
                throw new GitletException(ERROR_MSG);
            }
            List<String> files = Utils.plainFilenamesIn(COMMIT_OBJECT_DIR);
            if (files == null) {
                throw new GitletException(ERROR_MSG);
            }
            for (String filename: files) {
                if (filename.contains(commitFilename)) {
                    commitFilename = filename;
                    break;
                }
                throw new GitletException(ERROR_MSG);
            }
        }
        // File should exist now
        final File commitObjectFile = Utils.join(OBJECTS_DIR, commitDirname, commitFilename);
        try {
            return readCommitObject(commitObjectFile);
        } catch (GitletException e) {
            // Object file has wrong type
            throw new GitletException(ERROR_MSG);
        }
    }

    /**
     * Get the Commit OBJECT of the latest commit of <b>current branch</b>
     * Runtime: O(1) with N commits
     *          O(N) with commit of size N
     * @return The commit object described above
     */
    public static Commit getHeadCommit() {
        // FIXME: This calling chain seems redundent.
        File commitRefFile = readRootHead();
        File commitObjectFile = readCommitRef(commitRefFile);
        return readCommitObject(commitObjectFile);
    }

    /**
     * Get the head commit of a given branch
     * Runtime: O(1) with N branches
     * @param branch - the name of the branch to get from
     * @return The head commit of a branch
     * @throws GitletException - When branch doesn't exist
     */
    public static Commit getHeadCommit(String branch) throws GitletException {
        final File BRANCH_FILE = Utils.join(REFS_HEADS_DIR, branch);
        if (!BRANCH_FILE.exists()) {
            throw new GitletException("No such branch exists.");
        }
        File commitRefFile = Utils.join(REFS_HEADS_DIR, branch);
        File commitObjectFile = readCommitRef(commitRefFile);
        return readCommitObject(commitObjectFile);
    }

    /**
     * Get the staged commit from stage file.
     * Runtime: O(N) with stage file of size N
     * @return The staged commit
     */
    public static Commit getStagedCommit() {
        Commit staged;
        if (!STAGE_FILE.exists()) {
            Commit head = getHeadCommit();
            staged = Commit.createStagedCommit(head);
        } else {
            staged = readCommitObject(STAGE_FILE);
        }
        return staged;
    }

    /**
     * Create all directories for gitlet workspace
     * Called on init
     * <p>
     * Directories:
     * .gitlet
     * ├── logs
     * │   └ refs
     * │     └ heads
     * │── objects
     * └── refs
     * └ heads
     *
     * @throws IOException - When IO system fails
     */
    private static void makeEssentialDir() throws IOException {
        GITLET_DIR.mkdir();
        LOGS_DIR.mkdir();
        LOGS_REFS_DIR.mkdir();
        LOGS_REFS_HEADS_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        REFS_DIR.mkdir();
        REFS_HEADS_DIR.mkdir();
    }

    /**
     * Create files on init
     * <p>
     * Files:
     * .gitlet
     * ├── HEAD
     * └── logs
     * └ HEAD
     *
     * @throws IOException - When IO system fails
     */
    private static void createInitFile() throws IOException {
        LOGS_HEAD_FILE.createNewFile();
        ROOT_HEAD_FILE.createNewFile();
    }

    /**
     * Write a commit object to gitlet directory
     *
     * @param commit - The commit to be witten
     */
    private static void writeCommitFiles(Commit commit) {
        try {
            writeCommitLog(commit);
            writeCommitRef(commit);
            writeCommitObject(commit);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        } catch (GitletException e) {
            ErrorHandler.handleGitletException(e);
        }
    }

    /**
     * Create the initial commit and write to gitlet workspace
     * Init commit do not contain any blobs.
     *
     * @return The init commit object (No longer needed)
     */
    private static Commit makeInitCommit() {
        Commit initCommit = Commit.createInitCommit();
        writeCommitFiles(initCommit);
        return initCommit;
    }

    private static void writeStageFile(Commit stage) {
        assert stage != null && stage.isStaged();

        try {
            if (!STAGE_FILE.exists()) {
                STAGE_FILE.createNewFile();
            }
            Utils.writeObject(STAGE_FILE, stage);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    static void clearStageFile() {
        STAGE_FILE.delete();
    }

    private static void updateStageFileTo(String branch) {
        Commit stage = getStagedCommit();
        Commit headCommit = getHeadCommit(branch);
        stage.updateBlobs(headCommit.getAllBlobs());
        writeStageFile(stage);
    }

    /**
     * Write to .gitlet/HEAD
     * The content will be refs/heads/BRANCH
     * <p>
     * Called when:
     * 1. git commit
     * 2. git checkout BRANCH
     * <p>
     * NOTE: Currently it's hard coded to write refs/heads/BRANCH.
     * If we want to support detach or remotes, we should improve this.
     *
     * @param branch - the branch to write in
     * @throws IOException - When IO system fails
     */
    private static void updateRootHead(String branch) throws IOException {
        final String REFS_HEADS_PATH_STRING = "refs/heads/";
        ROOT_HEAD_FILE.delete();
        ROOT_HEAD_FILE.createNewFile();
        writeContents(ROOT_HEAD_FILE, REFS_HEADS_PATH_STRING + branch);
    }

    /**
     * Read the root HEAD
     *
     * @return the ref file pointed by the HEAD's content
     */
    private static File readRootHead() throws GitletException { // throws IOException {
        if (!ROOT_HEAD_FILE.exists()) {
            throw new GitletException("Broken gitlet repository: .gitlet/HEAD not found!");
        }
        final String rootContent = Utils.readContentsAsString(ROOT_HEAD_FILE);
        File refFile = Utils.join(GITLET_DIR, rootContent);
        return refFile;
    }

    /**
     * Write to .gitlet/refs/heads/COMMIT.BRANCH
     * <p>
     * Creates a new file whenever it is invoked.
     * Contians sha1 of the commit.
     *
     * @param commit the commit to write in
     * @throws IOException - When IO system fails
     */
    private static void writeCommitRef(Commit commit) throws IOException {
        writeCommitRef(commit.getBranch(), commit);
    }

    /**
     * Write to .gitlet/refs/heads/BRANCH
     * @param branch - Branch to write to
     * @param commit - Commit to write
     * @throws IOException - When IO system fails
     */
    private static void writeCommitRef(String branch, Commit commit) throws IOException {
        File refFile = Utils.join(REFS_HEADS_DIR, branch);
        refFile.delete();
        refFile.createNewFile();
        Utils.writeContents(refFile, commit.getSha1());
    }

    /**
     * Read the commit ref
     *
     * @param commitRefFile - the File returned by readRootHead(), assume exists
     * @return Object file of the commit.
     *  TODO: Better name?
     *  NOTE: Whether return is valid should be checked by the caller.
     */
    private static File readCommitRef(File commitRefFile) throws GitletException {
        if (!commitRefFile.exists()) {
            throw new GitletException("Broken gitlet directory!");
        }
        String sha1 = Utils.readContentsAsString(commitRefFile);
        File commitObjectFile = Utils.join(OBJECTS_DIR, sha1.substring(0, 2), sha1.substring(2));
        return commitObjectFile;
    }

    private static Commit readCommitObject(String commitSha1) throws GitletException {
        final String errorMsg = "Object file refered by commit ref doesn't exist!";
        File commitObjectFile = Utils.join(
                OBJECTS_DIR,
                commitSha1.substring(0, 2),
                commitSha1.substring(2)
        );
        return readGitletObject(commitObjectFile, Commit.class, errorMsg);
    }

    // WARN: The map is not necessarily right, considering it stores addresses.
    private static Commit readCommitObject(File commitObjectFile) throws GitletException {
        final String errorMsg = "Object file referred by commit ref doesn't exist!";
        return readGitletObject(commitObjectFile, Commit.class, errorMsg);
    }

    public static Blob readBlobObject(String blobSha1) throws GitletException {
        final String errorMsg = "Object file referred by blob ref doesn't exist!";
        File blobObjectFile = Utils.join(
                OBJECTS_DIR,
                blobSha1.substring(0, 2),
                blobSha1.substring(2)
        );
        return readGitletObject(blobObjectFile, Blob.class, errorMsg);
    }

    private static Blob readBlobObject(File blobObjectFile) throws GitletException {
        final String errorMsg = "Object file refered by blob ref doesn't exist!";
        return readGitletObject(blobObjectFile, Blob.class, errorMsg);
    }

    /**
     * Read a Serializable GitletObject from file
     * Runtime: O(1) with object of size N
     * @param objectFile - The file to read from
     * @param type       - The type of GitletObject (Commit or Blob)
     * @param errorMsg   - The error message to display on not found
     * @param <T>        The type of GitletObject (Commit or Blob)
     * @return The Gitlet Object according to Type T
     * @throws GitletException - When object file doesn't exists
     */
    private static <T extends Serializable> T readGitletObject(
            File objectFile, Class<T> type, String errorMsg
    ) throws GitletException {
        if (!objectFile.exists()) {
            throw new GitletException(errorMsg);
        }
        try {
            T object = Utils.readObject(objectFile, type);
            return object;
        } catch (IllegalArgumentException e) {
            ErrorHandler.handleJavaException(e);
            throw new AssertionError("not reached");
        } catch (ClassCastException e) {
            throw new GitletException(errorMsg);
        }
    }

    /**
     * Restore a file given the filename to the blob in the head commit
     * Runtime: O(1) with N files in the commit
     *          O(N) with file of size N
     * @param filename - filename of the file to be restored
     */
    public static void restoreFile(String filename) throws GitletException {
        Commit head = getHeadCommit();
        String blobSha1 = head.getBlobSha1(filename);
        if (blobSha1 != null) {
            // This one will throw its own GitletException, though
            // May need to improve
            restoreBlobContent(blobSha1);
        } else {
            throw new GitletException("File does not exist in that commit.");
        }
    }

    /**
     * Restore a file given the filename to the blob in the given commit
     * Runtime: O(1) with N total commits
     *          O(1) with N files in one commit
     *          O(N) with file of size N
     * @param commitID - The sha1 abbreviation of the commit (at least 5 char)
     * @param filename - Filename of the file to be restored
     * @throws GitletException - Filename doesn't exist in the given commit
     *                           Or commit with the ID doesn't exist
     */
    public static void restoreFile(String commitID, String filename) throws GitletException {
        Commit commit = getCommit(commitID);
        String blobSha1 = commit.getBlobSha1(filename);
        if (blobSha1 == null) {
            throw new GitletException("File does not exist in that commit.");
        }
        restoreBlobContent(blobSha1);
    }

    /**
     * Restore a file to the content of a blob,
     * creates a new file if non-existent
     * Runtime: O(N) with blob of size N
     *          O(1) with other factors
     * @param blobSha1 - The sha1 of the blob to restore to
     * @throws GitletException - When there is no blob of that sha1
     */
    public static void restoreBlobContent(String blobSha1) throws GitletException {
        Blob blob = readBlobObject(blobSha1);
        restoreBlobContent(blob);
    }

    /**
     * Restore a file to the content of a blob,
     * Runtime: O(N) with blob of size N
     *          O(1) with other factors
     * @param blob - The blob to restore to
     */
    private static void restoreBlobContent(Blob blob) {
        File blobFile = blob.getFile();
        byte[] data = blob.getData();
        try {
            if (!blobFile.exists()) {
                blobFile.createNewFile();
            }
            // Overwriting
            Utils.writeContents(blobFile, (Object) data);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    /**
     * Restore all files to the according blobs
     * Runtime: O(N) with N files in CWD, require O(1) HashMap
     *
     * @param blobs - The Map of filename-blob to restore to
     * @throws GitletException When there's an unstaged file
     * This exception should be caught and handled
     */
    private static void restoreAllFiles(Map<String, String> blobs) throws GitletException {
        HashSet<String> files = getCWDFiles();
        Commit headCommit = getHeadCommit();
        try {
            // Filter the FS and replace changed files with the ones in blobs
            for (String filename : files) {
                Blob cwdBlob = new Blob(filename);
                String otherSha1 = blobs.get(filename);
                String thisSha1 = headCommit.getBlobSha1(filename);
                Blob thisBlob = thisSha1 != null ? readBlobObject(thisSha1) : null;
                if (otherSha1 == null) {
                    // File doesn't exist in BLOBS
                    // If this file doesn't exist in head,
                    // Then it is an unstaged file (remain)
                    // Else it should be restored
                    if (thisBlob != null) {
                        File currentFile = new File(filename);
                        // TODO: Do not delete staged files (extra)
                        // First test if the file is unstaged
                        testUnstaged(filename, thisBlob, files);
                        // Then delete current file
                        currentFile.delete();
                    }
                } else if (!otherSha1.equals(cwdBlob.getSha1())) {
                    // File is modified in BLOBS
                    // First test if the file is unstaged
                    testUnstaged(filename, thisBlob, files);
                    // Then restore the blob
                    restoreBlobContent(otherSha1);
                }
            }
            // For the rest of blobs, they are new files
            for (Map.Entry<String, String> blobEntry : blobs.entrySet()) {
                File currentFile = new File(blobEntry.getKey());
                if (!currentFile.exists()) {
                    currentFile.createNewFile();
                    String blobSha1 = blobEntry.getValue();
                    restoreBlobContent(blobSha1);
                }
            }
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        } catch (GitletException e) {
            if (e.getMessage().equals(Errors.ERR_UNSTAGED)) {
                throw e;
            }
            ErrorHandler.handleGitletException(e);
        }
    }

    /**
     * Return a hashset containing all filenames of CWD
     * @return Hashset, empty if Utils return null
     */
    private static HashSet<String> getCWDFiles() {
        List<String> files = Utils.plainFilenamesIn(CWD);
        HashSet<String> set;
        if (files != null) {
            set = new HashSet<>(files);
        } else {
            set = new HashSet<>();
        }
        return set;
    }

    /**
     * Copy a branch's log to log's HEAD file
     * Same as:
     * <pre><code lang="shell">
     *     cp logs/refs/heads/[branch] logs/head
     * </code></pre>
     * Runtime: O(N) with N commits in a branch
     *
     * @param branch - The branch to update the log HEAD to
     * @throws IOException - When IO system fails
     */
    private static void updateLogsHead(String branch) throws IOException {
        final File BRANCH_LOG_FILE = Utils.join(LOGS_REFS_HEADS_DIR, branch);
        // NOTE: If assert happens it's either a bug or a programmer failure
        assert BRANCH_LOG_FILE.exists();
        Files.copy(BRANCH_LOG_FILE.toPath(), LOGS_HEAD_FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // private static File readLogsHead

    /**
     * Write a commit's log to gitlet workspace
     * File: logs/refs/[branch]
     * Runtime: O(1)
     *
     * @param commit The commit to be written
     * @throws IOException - When IO system fails
     */
    private static void writeCommitLog(final Commit commit) throws IOException {
        assert !commit.isStaged();
        String branch = commit.getBranch();
        final File COMMIT_LOG_FILE = Utils.join(LOGS_REFS_HEADS_DIR, branch);

        // WARN: Consider the case in which it exists, and not exists.
        if (!COMMIT_LOG_FILE.exists()) {
            COMMIT_LOG_FILE.createNewFile();
        }
        String previousContent = Utils.readContentsAsString(COMMIT_LOG_FILE);
        Utils.writeContents(
                COMMIT_LOG_FILE,
                previousContent,
                commit.getParent() != null
                        ? commit.getParent().getSha1()
                        : "0000000000000000000000000000000000000000",
                " ",
                commit.getSha1(),
                " ",
                Long.toString(commit.getTimestamp().getTime()),
                " ",
                commit.getMessage(), // In git, there'll be an indicator
                // whether it's a branch or commit
                "\n"
        );
        updateLogsHead(branch);
    }

    /**
     * Serialize a GitletObject instance to an object file,
     * placed in the directory objects/
     * Runtime: O(N) with object of size of N
     *
     * @param object - The serializable gitlet object
     * @throws IOException     - When IO System fails
     * @throws GitletException - When a hash collision occurred, should never happen
     */
    private static void writeGitletObject(GitletObject object) throws IOException, GitletException {
        final File OBJECT_DIR = Utils.join(OBJECTS_DIR, object.getSha1().substring(0, 2));
        if (!OBJECT_DIR.exists()) {
            OBJECT_DIR.mkdir();
        }
        final File OBJECT_FILE = Utils.join(OBJECT_DIR, object.getSha1().substring(2));
        if (OBJECT_FILE.exists()) {
            throw new GitletException("Object exists!");
        }
        OBJECT_FILE.createNewFile();
        Utils.writeObject(OBJECT_FILE, object);
    }

    /**
     * Serialize a commit to an object file
     * Runtime: O(N) with commit of N staged files
     *
     * @param commit - The commit object to write to file
     * @throws IOException     - When IO System fails
     * @throws GitletException - When a hash collision occurred, should never happen
     */
    private static void writeCommitObject(Commit commit) throws IOException, GitletException {
        writeGitletObject(commit);
    }

    /**
     * Serialize a blob to an object file
     *
     * @param blob - The blob to be written to file
     * @throws IOException     - When IO System fails
     * @throws GitletException - When a hash collision occurred, should never happen
     */
    private static void writeBlobObject(Blob blob) throws IOException, GitletException {
        writeGitletObject(blob);
    }

    /**
     * Update the working directory with the blobs in the COMMIT
     *
     * @param commit the commit to restore to
     * @throws GitletException When there's an unstaged file that will be overwritten
     */
    private static void restoreToCommit(Commit commit) throws GitletException {
        restoreAllFiles(commit.getAllBlobs());
    }

    /**
     * Get all unstaged files in the CWD
     *
     * @return - A SortedMap of filename-status pair
     */
    public static SortedMap<String, UnstagedStatus> getUnstagedFiles() {
        Commit staged = getStagedCommit();
        List<String> files = plainFilenamesIn(CWD);
        // TODO: Should construct a Set first, then sort it out
//        try {
//            for (String filename: files) {
//                if (staged.isFileNew(filename)) {
//                    unstaged.put(filename, UnstagedStatus.NEW);
//                } else if (staged.isBlobModified(new Blob(filename))) {
//                    unstaged.put(filename, UnstagedStatus.MODIFIED);
//                }
//            }
//            Set<String> deleted = staged.getAllDeleted(files);
//            for (String deletedFile: deleted) {
//                unstaged.put(deletedFile, UnstagedStatus.DELETED);
//            }
//        } catch (IOException e) {
//            ErrorHandler.handleJavaException(e);
//        }
        SortedMap<String, UnstagedStatus> unstaged = staged.getUnstaged(files);
        return unstaged;
    }

    /**
     * Check whether the CWD has unstaged changes
     *
     * @return true on has, false otherwise
     */
    public static boolean hasUnstagedChanges() {
        Commit staged = getStagedCommit();
        List<String> files = plainFilenamesIn(CWD);
        return staged.hasUnstaged(files);
    }

    enum UnstagedStatus {DELETED, MODIFIED, NEW}
}
