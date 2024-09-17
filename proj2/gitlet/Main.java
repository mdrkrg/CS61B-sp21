package gitlet;

/**
 * Driver class for Gitlet, a subset of the Git version-control system.
 *
 * @author TODO
 */
public class Main {

    /**
     * Usage: java gitlet.Main ARGS, where ARGS contains
     * <COMMAND> <OPERAND1> <OPERAND2> ...
     */
    public static void main(String[] args) {
        // TODO: what if args is empty?
        ErrorHandler.handleNoCommand(args);
        commandSelector(args);
    }

    private static void commandSelector(String[] args) {
        String firstArg = args[0];
        selectInit(firstArg); // FIXME: This is not clean
        ErrorHandler.handleGitletNotExist();
        switch (firstArg) {
            case "test" -> {
                Command.testHead();
                Command.testStaged();
            }
            case "add"        -> Command.add(args);
            case "rm"         -> Command.rm(args);
            case "commit"     -> Command.commit(args);
            case "log"        -> Command.log(args);
            case "branch"     -> Command.branch(args);
            case "checkout"   -> Command.checkout(args);
            case "switch"     -> Command.switchTo(args);
            case "status"     -> Command.status(args);
            case "global-log" -> Command.globalLog(args);
            case "find"       -> Command.find(args);
            case "rm-branch"  -> Command.rmBranch(args);
            case "reset"      -> Command.reset(args);
            default           -> ErrorHandler.handleCommandNotFound();
        }
    }

    private static void selectInit(String firstArg) {
        if (!firstArg.equals("init")) {
            return;
        }
        ErrorHandler.handleGitletExist();
        // TODO: Repository.initGitlet();
        Command.init();
        System.exit(0);
    }
}
