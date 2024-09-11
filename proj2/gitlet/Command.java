package gitlet;

public class Command {
    static void init() {
        Repository.init();
    }

    static void add(String[] args) {
        if (args.length != 2) {
            ErrorHandler.handleInvalidOperands();
        }
        String filename = args[1];
        try {
            Repository.add(filename);
        } catch (GitletException e) {
            ErrorHandler.handleGitletException(e);
        }
    }

    static void testHead() {
        System.out.println(Repository.getBranch());
        Commit headCommit = Repository.getHeadCommit();
        headCommit.printCommitInfo();
    }

    static void testStaged() {
        Commit staged = Repository.getStagedCommit();
        staged.printBlobInfo();
    }
}