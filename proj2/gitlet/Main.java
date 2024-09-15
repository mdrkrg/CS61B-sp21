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
            case "test":
                Command.testHead();
                Command.testStaged();
                break;
            case "add":
                Command.add(args);
                break;
            case "rm":
                Command.rm(args);
                break;
            case "commit":
                Command.commit(args);
                break;
            case "log":
                Command.log(args);
                break;
            case "branch":
                Command.branch(args);
                break;
            case "checkout":
                Command.checkout(args);
                break;
            case "switch":
                Command.switchTo(args);
                break;
            case "status":
                Command.status(args);
                break;
            // TODO: FILL THE REST IN
            default:
                ErrorHandler.handleCommandNotFound();
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