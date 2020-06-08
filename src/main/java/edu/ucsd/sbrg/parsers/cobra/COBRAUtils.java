package edu.ucsd.sbrg.parsers.cobra;

import de.zbit.util.ResourceManager;
import us.hebi.matlab.mat.types.Array;
import us.hebi.matlab.mat.types.Cell;
import us.hebi.matlab.mat.types.Char;
import us.hebi.matlab.mat.types.MatlabType;

import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

public class COBRAUtils {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(SpeciesParser.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");

  /**
   * @param string
   *        the {@link String} to be tested.
   * @return {@code true} if the given {@link String} is either {@code null} or
   *         empty or equals empty square brackets.
   */
  public static boolean isEmptyString(String string) {
    return (string == null) || string.isEmpty() || string.equals("[]");
  }


  /**
   * Necessary to check for a special whitespace (code 160) at beginning of id
   * (iCHOv1.mat, possibly other models) and to remove trailing ';'
   *
   * @param id
   * @return trimmed id without ';' at the end
   */
  public static String checkId(String id) {
    if (id.startsWith("InChI")) {
      return id;
    }
    if (id.startsWith(Character.toString((char) 160)) || id.startsWith("/")) {
      id = id.substring(1);
    }
    if (id.endsWith(";")) {
      id = id.substring(0, id.length() - 1);
    } else if (id.contains(";")) {
      logger.warning(MESSAGES.getString("TRUNCATED_ID") + id);
      id = id.substring(0, id.indexOf(";"));
    }
    return id;
  }


  /**
   * @param cell
   * @param i
   * @return
   */
  public static boolean exists(Array cell, int i) {
    if (cell != null) {
      if (cell instanceof Cell) {
        return (((Cell) cell).get(i) != null);
      }
      return true;
    }
    return false;
  }


  /**
   * @param exc
   */
  public static void logException(Exception exc) {
    logger.warning(format("{0}: {1}", exc.getClass().getSimpleName(), de.zbit.util.Utils.getMessage(exc)));
  }


  /**
   * @param array
   * @return
   */
  public static String asString(Array array) {
    return asString(array, null, -1);
  }


  /**
   * @param array
   * @param parentName
   * @param parentIndex
   * @return
   */
  public static String asString(Array array, String parentName, int parentIndex) {
    StringBuilder sb = new StringBuilder();
    if (array.getType() == MatlabType.Character) {
      Char string = (Char) array;
      if (string.getDimensions()[0] > 1) {
        logger.fine(format(MESSAGES.getString("MANY_STRINGS_IN_CELL"), string.asCharSequence()));
      }
      for (int i = 0; i < string.getDimensions()[0]; i++) {
        if (i > 0) {
          sb.append('\n');
        }
        sb.append(string.getRow(i));
      }
    } else if (!Arrays.equals(array.getDimensions(), new int[] {0, 0})) {
      logger.warning(format(MESSAGES.getString("TYPE_MISMATCH_STRING"), array.getType().toString(), "parentName = %s",
        parentName, "parentIndex = %s", parentIndex));
    }
    return sb.toString();
  }
}
