package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

import static gitlet.Utils.*;

// TODO: any imports you need here

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author TODO
 */
public class Repository {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR          = join(CWD, ".gitlet");
    // public static final File BRANCHES_DIR      = join(GITLET_DIR, "branches");
    public static final File STAGE_FILE          = join(GITLET_DIR, "stage");
    public static final File LOGS_DIR            = join(GITLET_DIR, "logs");
    public static final File LOGS_HEAD_FILE      = join(GITLET_DIR, "logs", "HEAD");
    public static final File LOGS_REFS_DIR       = join(GITLET_DIR, "logs", "refs");
    public static final File LOGS_REFS_HEADS_DIR = join(GITLET_DIR, "logs", "refs", "heads");
    public static final File ROOT_HEAD_FILE      = join(GITLET_DIR, "HEAD");
    public static final File OBJECTS_DIR         = join(GITLET_DIR, "objects");
    public static final File REFS_DIR            = join(GITLET_DIR, "refs");
    public static final File REFS_HEADS_DIR      = join(GITLET_DIR, "refs", "heads");

    public static final String DEFAULT_BRANCH = "master";

    /* TODO: fill in the rest of this class. */

    /** Init the gitlet Repository
     *  Assume that the repository has not yet been inited.
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

    /** Add a file to stage.
     *
     *    There're five cases in total that can occur:
     *    1. File not changed : filename equals, sha1 equals
     *    2. File changed     : filename equals, sha1 unequals
     *    3. File renamed     : filename unequals, sha1 equals (how?) (two ops)
     *      - If you use an iterator to iterate through all entries in
     *        the blobs and check if sha1 equals, that will be O(n) time
     *      - Git uses two lists: One deleted, one Added, how to implement?
     *    4. File is new      : filename doesn't exist
     *    5. File deleted     : filename exists, !file.exists()
     *    6. File changed and renamed??? -> separate to two
     *
     *    // WARN: If switch a branch with staged changes,
     *             the target branch may contain same file as the staged
     *
     *  @param filename file to be staged.
     */
    static void add(final String filename) {
        Commit staged = getStagedCommit();
        try {
            Blob blob = new Blob(filename);
            Boolean addSuccessful = staged.addToStage(blob);
            if (addSuccessful) {
                writeStageFile(staged);
            }
        } catch (GitletException e) {
            // File not exist in workspace, Case 5 or error
            boolean removeSuccessful = staged.remove(filename);
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

    /** Remove a file from staged
     *  If the file is exisiting in workspace, rm it
     *
     *  @param filename file to remove from stage
     */
    static void remove(final String filename) {
        Commit staged = getStagedCommit();
        boolean stageRemoveSuccess = staged.removeFromStage(filename);
        boolean blobsRemoveSuccess = staged.removeFromBlobs(filename);

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

    static Commit commit(final String message) throws GitletException {
        Commit staged = getStagedCommit();
        final String branch = getCurrentBranch();
        if (!staged.hasStagedChanges()) {
            throw new GitletException("No changes added to the commit.");
        }
        if (message.isBlank()) {
            throw new GitletException("Please enter a commit message.");
        }
        Commit newCommit = Commit.finishCommit(staged, branch, message, new Date());
        writeCommitFiles(newCommit);
        clearStageFile();
        return newCommit;
    }

    static void log() {
        Commit head = getHeadCommit();
        while (head != null) {
            System.out.println("===");
            head.printCommitInfo();
            System.out.println();
            head = head.getParent();
        }
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
            // TODO: Update filesystem
            updateRootHead(name);
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
        for (Map.Entry<String, UnstagedStatus> entry: unstaged.entrySet()) {
            String filename = entry.getKey();
            switch (entry.getValue()) {
                case NEW:
                    newFiles.add(filename);
                    break;
                case MODIFIED:
                    System.out.printf("%s (modified)\n", filename);
                    break;
                case DELETED:
                    System.out.printf("%s (deleted)\n", filename);
                    break;
            }
        }
        System.out.println();
        System.out.println("=== Untracked Files ===");
        for (String filename: newFiles) {
            System.out.println(filename);
        }
        System.out.println();
    }

    static void printBranches() {
        System.out.println("=== Branches ===");
        String currentBranch = getCurrentBranch();
        System.out.printf("*%s\n", currentBranch);
        List<String> branches = getBranches();
        for (String branch: branches) {
            if (!branch.equals(currentBranch)) {
                System.out.println(branch);
            }
        }
        System.out.println();
    }

    private static List<String> getBranches() {
        if (!REFS_HEADS_DIR.exists()) {
            throw new GitletException("Broken Gitlet Repository!");
        }
        return Utils.plainFilenamesIn(REFS_HEADS_DIR);
    }

    public static String getCurrentBranch() throws GitletException {
        if (!ROOT_HEAD_FILE.exists()) {
            throw new GitletException("Broken gitlet repository: .gitlet/HEAD not found!");
        }
        final String rootContent = Utils.readContentsAsString(ROOT_HEAD_FILE);
        // final String[] tokens = rootContent.split("/");
        // assert tokens.length == 3;
        String branch = rootContent.substring(rootContent.lastIndexOf("/") + 1);
        return branch;
    }

    public static Commit getHeadCommit() {
        // FIXME: This calling chain seems redundent.
        File commitRefFile = readRootHead();
        File commitObjectFile = readCommitRef(commitRefFile);
        return readCommitObject(commitObjectFile);
    }

    /** Get the staged commit from stage file.
     *
     *  Runtime: O(N) with stage file of size N
     *  @return The staged commit
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

    private static void makeEssentialDir() throws IOException {
        GITLET_DIR.mkdir();
        LOGS_DIR.mkdir();
        LOGS_REFS_DIR.mkdir();
        LOGS_REFS_HEADS_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        REFS_DIR.mkdir();
        REFS_HEADS_DIR.mkdir();
    }

    private static void createInitFile() throws IOException {
        LOGS_HEAD_FILE.createNewFile();
        ROOT_HEAD_FILE.createNewFile();
    }

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

    /** Write to .gitlet/HEAD
     *  The content will be refs/heads/BRANCH
     *
     *  Called when:
     *    1. git commit
     *    2. git checkout BRANCH
     *
     *  NOTE: Currently it's hard coded to write refs/heads/BRANCH.
     *        If we want to support detach or remotes, we should improve this.
     *
     *  @param  branch - the branch to write in
     *  @throws IOException - When IO fails
     */
    private static void updateRootHead(String branch) throws IOException {
        final String REFS_HEADS_PATH_STRING = "refs/heads/";
        ROOT_HEAD_FILE.delete();
        ROOT_HEAD_FILE.createNewFile();
        writeContents(ROOT_HEAD_FILE, REFS_HEADS_PATH_STRING + branch);
    }

    /** Read the root HEAD
     *  @return the ref file pointed by the HEAD's content
     */
    private static File readRootHead() throws GitletException { // throws IOException {
        if (!ROOT_HEAD_FILE.exists()) {
            throw new GitletException("Broken gitlet repository: .gitlet/HEAD not found!");
        }
        final String rootContent = Utils.readContentsAsString(ROOT_HEAD_FILE);
        File refFile = Utils.join(GITLET_DIR, rootContent);
        return refFile;
    }

    /** Write to .gitlet/refs/heads/COMMIT.BRANCH
     *
     *  Creates a new file whenever it is invoked.
     *  Contians sha1 of the commit.
     *
     *  @param  commit the commit to write in
     *  @throws IOException
     */
    private static void writeCommitRef(Commit commit) throws IOException {
        File refFile = Utils.join(REFS_HEADS_DIR, commit.getBranch());
        refFile.delete();
        refFile.createNewFile();
        Utils.writeContents(refFile, commit.getSha1());
    }

    /** Read the commit ref
     *
     *  @param  commitRefFile - the File returned by readRootHead(), assume exists
     *  @return Object file of the commit.
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

    // WARN: The map is not necessarily right, considering it stores addresses.
    private static Commit readCommitObject(File commitObjectFile) throws GitletException {
        if (!commitObjectFile.exists()) {
            throw new GitletException("Object file refered by commit ref doesn't exist!");
        }
        try {
            Commit commit = Utils.readObject(commitObjectFile, Commit.class);
            return commit;
        } catch (IllegalArgumentException e) {
            ErrorHandler.handleJavaException(e);
            throw new AssertionError("not reached");
        }
    }

    private static void updateLogsHead(String branch) throws IOException {
        final File BRANCH_LOG_FILE = Utils.join(LOGS_REFS_HEADS_DIR, branch);
        // NOTE: If assert happens it's either a bug or a programmer failure
        assert BRANCH_LOG_FILE.exists();
        Files.copy(BRANCH_LOG_FILE.toPath(), LOGS_HEAD_FILE.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    // private static File readLogsHead

    private static void writeCommitLog(Commit commit) throws IOException {
        assert !commit.isStaged();
        String branch = commit.getBranch();
        final File COMMIT_LOG_FILE = Utils.join(LOGS_REFS_HEADS_DIR, branch);

        // WARN: Consider the case in which it exists, and not exists.
        if (!COMMIT_LOG_FILE.exists()) {
            COMMIT_LOG_FILE.createNewFile();
        }
        Utils.writeContents(
            COMMIT_LOG_FILE,
            commit.getParent() != null ? commit.getParent().getSha1() : "0000000000000000000000000000000000000000",
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

    private static void writeCommitObject(Commit commit) throws IOException, GitletException {
        final File OBJECT_DIR = Utils.join(OBJECTS_DIR, commit.getSha1().substring(0, 2));
        if (!OBJECT_DIR.exists()) {
            OBJECT_DIR.mkdir();
        }
        final File OBJECT_FILE = Utils.join(OBJECT_DIR, commit.getSha1().substring(2));
        if (OBJECT_FILE.exists()) {
            throw new GitletException("Object exists!");
        }
        OBJECT_FILE.createNewFile();
        Utils.writeObject(OBJECT_FILE, commit);
    }

    /** Update the working directory with the blobs in the COMMIT
     *
     *  @param commit the commit to restore to
     */
    private static void restoreToCommit(Commit commit) {
        List<String> filenames = Utils.plainFilenamesIn(CWD);
        for (String filename: filenames) {
            File f = new File(filename);
            // TODO:
        }
    }

    enum UnstagedStatus { DELETED, MODIFIED, NEW }
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

    public static boolean hasUnstagedChanges() {
        Commit staged = getStagedCommit();
        List<String> files = plainFilenamesIn(CWD);
        return staged.hasUnstaged(files);
    }
}