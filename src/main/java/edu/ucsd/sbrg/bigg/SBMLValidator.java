package edu.ucsd.sbrg.bigg;

import java.io.File;

/**
 * @author Andreas Dr&auml;ger
 */
public class SBMLValidator {

  /**
   * @param args
   *        the paths to at least one SBML file that is to be validated. When
   *        directories are encountered, these are recursively evaluated.
   */
  public static void main(String... args) {
    // System.setOut(new PrintStream(new File("output.txt")));
    for (String arg : args) {
      File file = new File(arg);
      if (file.isDirectory()) {
        main(file.list());
      } else {
        System.out.println(file.getName());
        org.sbml.jsbml.validator.SBMLValidator.main(new String[] {"-d", "p,u", file.getAbsolutePath()});
      }
    }
  }
}
