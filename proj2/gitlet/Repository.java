package gitlet;

import java.io.File;
import java.io.IOException;
import static gitlet.Utils.*;
import gitlet.FinishedCommit;

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
    public static final File GITLET_DIR     = join(CWD, ".gitlet");
    // public static final File BRANCHES_DIR = join(GITLET_DIR, "branches");
    public static final File LOGS_DIR       = join(GITLET_DIR, "logs");
    public static final File LOGS_HEAD_FILE = join(LOGS_DIR, "HEAD");
    public static final File LOGS_REFS_DIR  = join(LOGS_DIR, "refs");
    public static final File ROOT_HEAD_FILE = join(LOGS_DIR, "HEAD");
    public static final File OBJECTS_DIR    = join(GITLET_DIR, "objects");
    public static final File REFS_DIR       = join(GITLET_DIR, "refs");

    /* TODO: fill in the rest of this class. */

    /** Init the gitlet Repository
     *  Assume that the repository has not yet been inited.
     */
    static void init() {
        try {
            makeEssentialDir();
            createInitFile();
            makeInitCommit();
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        }
    }

    private static void makeEssentialDir() throws java.io.IOException {
        GITLET_DIR.mkdir();
        LOGS_DIR.mkdir();
        LOGS_REFS_DIR.mkdir();
        OBJECTS_DIR.mkdir();
        REFS_DIR.mkdir();
    }

    private static void createInitFile() throws java.io.IOException {
        LOGS_HEAD_FILE.createNewFile();
        ROOT_HEAD_FILE.createNewFile();
    }

    // WARN: Not a proper place for this class.
    private static void makeInitCommit() {
        FinishedCommit initCommit = InitialCommit.create();
        try {
            writeCommitText(initCommit);
            writeFinishedCommitObject(initCommit);
        } catch (IOException e) {
            ErrorHandler.handleJavaException(e);
        } catch (GitletException e) {
            ErrorHandler.handleGitletException(e);
        }
    }

    private static void writeCommitText(FinishedCommit commit) throws IOException {
        final File COMMIT_LOG_FILE = Utils.join(LOGS_REFS_DIR, commit.branch);
        if (!COMMIT_LOG_FILE.exists()) {
            COMMIT_LOG_FILE.createNewFile();
        }
        Utils.writeContents(
            COMMIT_LOG_FILE,
                 commit.parent != null ? commit.parent.sha1 : "", " ",
            commit.getSha1(), " ",
            commit.getTimestamp().toString(), " ",
            commit.getMessage(), // In git, there'll be an indicator
                                 // whether it's a branch or commit
            "\n"
        );
    }

    private static void writeFinishedCommitObject(FinishedCommit commit) throws IOException, GitletException {
        final File OBJECT_DIR = Utils.join(OBJECTS_DIR, commit.sha1.substring(0, 2));
        if (!OBJECT_DIR.exists()) {
            OBJECT_DIR.mkdir();
        }
        final File OBJECT_FILE = Utils.join(OBJECT_DIR, commit.sha1.substring(2));
        if (OBJECT_FILE.exists()) {
            throw new GitletException("Object exists!");
        }
        OBJECT_FILE.createNewFile();
        Utils.writeObject(OBJECT_FILE, commit);
    }
}