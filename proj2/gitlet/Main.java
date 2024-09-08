package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author TODO
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
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
        switch(firstArg) {
            case "add":
                // TODO: handle the `add [filename]` command
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
        System.exit(0);
    }
}
