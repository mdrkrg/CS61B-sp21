package gitlet;

class ErrorHandler {
    private static String ERR_NOCMD_MSG     = "Please enter a command.";
    private static String ERR_CMD_MSG      = "No command with that name exists.";
    private static String ERR_INVAL_MSG    = "Incorrect operands.";
    private static String ERR_DIR_MSG      = "Not in an initialized Gitlet directory.";
    private static String ERR_GITEXIST_MSG = "A Gitlet version-control system already exists in the current directory.";
    private static void exitWithMessage(String msg) {
        System.out.println(msg);
        System.exit(0);
    }

    static void handleNoCommand(String[] args) {
        if (args.length == 0) {
            exitWithMessage(ERR_NOCMD_MSG);
        }
    }

    static void handleGitletNotExist() {
        if (!Repository.GITLET_DIR.exists()) {
            exitWithMessage(ERR_DIR_MSG);
        }
    }

    static void handleGitletExist() {
        if (Repository.GITLET_DIR.exists()) {
            exitWithMessage(ERR_GITEXIST_MSG);
        }
    }

    static void handleCommandNotFound() {
        exitWithMessage(ERR_CMD_MSG);
    }

    static void handleInvalidOperands() {
        exitWithMessage(ERR_INVAL_MSG);
    }
}
