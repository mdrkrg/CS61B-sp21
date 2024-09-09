package gitlet;

import java.util.Date;

// TODO: Improve class structure
public class InitialCommit {
    private static final Date   INIT_TIMESTAMP = new Date(0);
    private static final String INIT_MESSAGE   = "initial commit";
    private static final String DEFAULT_BRANCH = "master";

    public static FinishedCommit create() {
        return FinishedCommit.fromStaged(new StagedCommit(null), DEFAULT_BRANCH, INIT_MESSAGE, INIT_TIMESTAMP);
    }
}