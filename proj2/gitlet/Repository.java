package gitlet;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.*;

import static gitlet.Utils.*;

/**
 * Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
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

    /* TODO: fill in the rest of this class. */

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
        // FIXME: When file change back, it's still in stage
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
                writeBlobObject(b);
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

        if (hasUnstagedChanges()) {
            throw new GitletException(
                    "There is an untracked file in the way; delete it, or add and commit it first."
            );
        }
        try {
            Commit branchHead = getHeadCommit(name);
            restoreToCommit(branchHead);
            updateRootHead(name);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    static void reset(String commitID) throws GitletException {
        // Throws "no commit exist" if not found
        Commit commit = getCommit(commitID);
        if (hasUnstagedChanges()) {
            // FIXME: If a working file is untracked in the current branch
            //        and WOULD BE OVERWRITTEN by the reset
            throw new GitletException(
                    "There is an untracked file in the way; delete it, or add and commit it first."
            );
        }
        try{
            writeCommitRef(getCurrentBranch(), commit);
            // For log, simply don't update them
            restoreToCommit(commit);
            clearStageFile();
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
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

    private static void clearStageFile() {
        STAGE_FILE.delete();
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
     * TODO: Conditionally restore file if no difference,
     *       may drop performance.
     * Runtime: O(N) with N files in CWD, require O(1) HashMap
     *
     * @param blobs - The Map of filename-blob to restore to
     */
    private static void restoreAllFiles(Map<String, String> blobs) {
        List<String> files = Utils.plainFilenamesIn(CWD);
        try {
            // Filter the FS and replace changed files with the ones in blobs
            assert files != null;
            for (String filename : files) {
                Blob currentBlob = new Blob(filename);
                String otherSha1 = blobs.get(filename);
                if (otherSha1 == null) {
                    File currentFile = new File(filename);
                    // TODO: Do not delete staged files (extra)
                    currentFile.delete();
                } else if (!otherSha1.equals(currentBlob.getSha1())) {
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
            ErrorHandler.handleGitletException(e);
        }
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
     */
    private static void restoreToCommit(Commit commit) {
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
