package gitlet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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
    public static final File LOGS_DIR            = join(GITLET_DIR, "logs");
    public static final File LOGS_HEAD_FILE      = join(GITLET_DIR, "logs", "HEAD");
    public static final File LOGS_REFS_DIR       = join(GITLET_DIR, "logs", "refs");
    public static final File LOGS_REFS_HEADS_DIR = join(GITLET_DIR, "logs", "refs", "heads");
    public static final File ROOT_HEAD_FILE      = join(GITLET_DIR, "HEAD");
    public static final File OBJECTS_DIR         = join(GITLET_DIR, "objects");
    public static final File REFS_DIR            = join(GITLET_DIR, "refs");
    public static final File REFS_HEADS_DIR      = join(GITLET_DIR, "refs", "heads");

    public static final String DEFAULT_BRANCH = "master";
    public static final String REFS_HEADS_PATH_STRING = "refs/heads/";

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

    public static String getBranch() throws GitletException {
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

    private static Commit makeInitCommit() {
        Commit initCommit = Commit.createInitCommit();
        try {
            writeCommitLog(initCommit);
            writeCommitRef(initCommit);
            writeCommitObject(initCommit);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        } catch (GitletException e) {
            ErrorHandler.handleGitletException(e);
        }
        return initCommit;
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
}
