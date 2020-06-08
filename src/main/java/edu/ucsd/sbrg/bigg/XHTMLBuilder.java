package edu.ucsd.sbrg.bigg;

import java.util.Map;

/**
 * @author Andreas Dr&auml;ger
 */
public class XHTMLBuilder {

  /**
   * 
   */
  private XHTMLBuilder() {
  }


  /**
   * @param header
   * @param data
   * @param caption
   * @param attributes
   * @return
   */
  public static String table(Object[] header, Object[][] data, String caption, Map<String, String> attributes) {
    StringBuilder sb = new StringBuilder();
    sb.append("<table");
    if (attributes != null) {
      for (Map.Entry<String, String> entry : attributes.entrySet()) {
        appendAttribute(sb, entry);
      }
    }
    sb.append(">\n");
    if (caption != null) {
      sb.append("<caption>");
      sb.append(caption);
      sb.append("</caption>\n");
    }
    if ((header != null) && (header.length > 0)) {
      sb.append("<tr>");
      for (Object head : header) {
        sb.append("<th>");
        sb.append(head.toString());
        sb.append("</th>");
      }
      sb.append("</tr>\n");
    }
    if ((data != null) && (data.length > 0)) {
      for (Object[] row : data) {
        if ((row != null) && (row.length > 0)) {
          sb.append("<tr>");
          for (Object entry : row) {
            sb.append("<td>");
            sb.append(entry.toString());
            sb.append("</td>");
          }
          sb.append("</tr>\n");
        }
      }
    }
    sb.append("</table>\n");
    return sb.toString();
  }


  /**
   * @param sb
   * @param entry
   */
  public static void appendAttribute(StringBuilder sb, Map.Entry<String, String> entry) {
    appendAttribute(sb, entry.getKey(), entry.getValue());
  }


  /**
   * @param sb
   * @param key
   * @param value
   */
  public static void appendAttribute(StringBuilder sb, String key, String value) {
    if ((sb.length() > 0) && (sb.charAt(sb.length() - 1) != ' ')) {
      sb.append(' ');
    }
    sb.append(key);
    sb.append("=\"");
    sb.append(value);
    sb.append('"');
  }


  /**
   * @param content
   * @return
   */
  public static String p(String content) {
    StringBuilder sb = new StringBuilder();
    sb.append("<p>\n");
    sb.append(content);
    sb.append("\n</p>\n");
    return sb.toString();
  }
}
