package gitlet;

public class Errors {
    public static String ERR_UNSTAGED =
                    "There is an untracked file in the way; delete it, or add and commit it first.";

    public static GitletException UnstagedException() {
        return new GitletException(ERR_UNSTAGED);
    }
}
