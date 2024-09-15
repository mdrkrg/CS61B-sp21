package gitlet;

import java.io.Serializable;

public interface GitletObject extends Serializable {
    public String getSha1();
}