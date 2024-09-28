package testing.student_tests;

import static org.junit.Assert.*;

import gitlet.Repository;
import gitlet.*;
import org.junit.Test;

import java.io.IOException;

public class GitletTest {
  /** @Test
  public void testMergeStatus() {
    int status;
    status = Repository.getMergeStatus("1", "2", "3");
    assertEquals(8, status);
    status = Repository.getMergeStatus("1", null, null);
    assertEquals(3, status);
    status = Repository.getMergeStatus("1", "2", "2");
    assertEquals(3, status);
    status = Repository.getMergeStatus("1", "2", "1");
    assertEquals(1, status);
    status = Repository.getMergeStatus("1", "1", "3");
    assertEquals(2, status);
    status = Repository.getMergeStatus("1", null, "1");
    assertEquals(6, status);
    status = Repository.getMergeStatus("1", "1", null);
    assertEquals(7, status);
    status = Repository.getMergeStatus("1", "2", null);
    assertEquals(8, status);
    status = Repository.getMergeStatus("1", null, "3");
    assertEquals(8, status);
  } */

  /**
   * Run the script test_multiple_parents.sh first, then use this test
   */

  static final String WORKING_DIR = "/home/crvena/Learning/CS61B/repo-21/proj2/testing/student_tests/";
  static final String TESTING_DIR = "/home/crvena/Learning/CS61B/repo-21/proj2/testing/student_tests/gittest/";

  /**
   * Test the new implementation of method Repository.getCommonAncestor
   */
  @Test
  public void testCommonAncestorNew() {
    System.setProperty("user.dir", WORKING_DIR);
    try {
      Runtime.getRuntime().exec(new String[]{"zsh", "-c", "./test_parents_pre.sh"});
      System.setProperty("user.dir", TESTING_DIR);
      Commit ancestor = Repository.getCommonAncestor("B1", "B2");
      assertEquals("f.txt added", ancestor.getMessage());
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
  }
}
