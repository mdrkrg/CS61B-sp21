package gitlet;

import java.io.Serializable;

interface GitletObject extends Serializable {
    String getSha1();
}
