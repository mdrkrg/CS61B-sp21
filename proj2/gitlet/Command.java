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

    static void rm(String[] args) {
        if (args.length != 2) {
            ErrorHandler.handleInvalidOperands();
        }
        String filename = args[1];
        try {
            Repository.remove(filename);
        } catch (GitletException e) {
            ErrorHandler.handleGitletException(e);
        }
    }

    static void commit(String[] args) {
        if (args.length > 2) {
            ErrorHandler.handleInvalidOperands();
        }
        String message;
        if (args.length == 1) {
            message = "";
        } else {
            message = args[1];
        }
        try {
            Repository.commit(message);
        } catch (GitletException e) {
            ErrorHandler.handleGitletException(e);
        }
    }

    static void log(String[] args) {
        // TODO: Branch
        validateArgCount(args, 1);
        Repository.log();
    }

    static void branch(String[] args) {
        validateArgCount(args, 2);
        String name = args[1];
        try {
            Repository.createNewBranch(name);
        } catch (GitletException e) {
            ErrorHandler.handleGitletException(e);
        }
    }

    static void checkout(String[] args) {
        final String sep = "--";
        try {
            switch (args.length) {
                case 2:
                    final String branchName = args[1];
                    Repository.switchToBranch(branchName);
                    break;
                case 3:
                    // Continue only when separator fit syntax
                    if (args[1].equals(sep)) {
                        final String fileName = args[2];
                        // Repository.pickFile(fileName);
                        throw new AssertionError("Not Implemented");
                        // break;
                    }
                case 4:
                    if (args[2].equals(sep)) {
                        // Continue only when separator fit syntax
                        final String commitID = args[1];
                        final String fileName = args[3];
                        // Repository.pickFile(commitID, fileName);
                        throw new AssertionError("Not Implemented");
                        // break;
                    }
                default:
                    ErrorHandler.handleInvalidOperands();
            }
        } catch (GitletException e) {
            ErrorHandler.handleGitletException(e);
        }
    }

    static void status(String[] args) {
        validateArgCount(args, 1);
        Repository.printStatus();
    }


    static void testHead() {
        System.out.println(Repository.getCurrentBranch());
        Commit headCommit = Repository.getHeadCommit();
        headCommit.printCommitInfo();
    }

    static void testStaged() {
        Commit staged = Repository.getStagedCommit();
        staged.printBlobInfo();
    }

    private static void validateArgCount(String[] args, int argc) {
        if (args.length != argc) {
            ErrorHandler.handleInvalidOperands();
        }
    }
}