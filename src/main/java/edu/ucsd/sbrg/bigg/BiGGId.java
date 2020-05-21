package edu.ucsd.sbrg.bigg;

import java.util.Optional;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.text.MessageFormat.format;

/**
 * This class stores the information from BiGG identifiers and provides methods
 * to access all components of the identifier.
 * For a formal description of the structure of BiGG ids see the proposed
 * <a href=
 * "https://github.com/SBRG/bigg_models/wiki/BiGG-Models-ID-Specification-and-Guidelines">
 * BiGG ID specification</a>.
 *
 * @author Andreas Dr&auml;ger
 * @author Thomas Zajac
 */
public class BiGGId {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(BiGGId.class.getName());
  /**
   * First part of BiGG ID, either R, M or G
   */
  private String prefix;
  /**
   * Second part of BiGG ID, matches [a-zA-Z0-9][a-zA-Z0-9_]+[a-zA-Z0-9]
   */
  private String abbreviation;
  /**
   * Third part of BiGG ID, matches [a-z][a-z0-9]? and exists for compartmentalized metabolites
   */
  private String compartmentCode;
  /**
   * Fourth part of BiGG ID, matches [A-Z][A-Z0-9]
   */
  private String tissueCode;

  /**
   *
   */
  enum IDPattern {

    ATPM("[Aa][Tt][Pp][Mm]"),
    BIOMASS("(([Rr]_?)?[Bb][Ii][Oo][Mm][Aa][Ss][Ss])(?:_(.+))?"),
    COMPARTMENT("[a-zA-Z][a-zA-Z0-9]?"),
    METABOLITE_SPECIAL("M_(?<abbreviation>[a-zA-Z0-9])_(?<compartment>[a-z][a-z0-9]?)"),
    PSEUDO("(([Rr]_?)?[Ee][Xx]_).*|(([Rr]_?)?[Dd][Mm]_).*|(([Rr]_?)?[Ss]([Ii][Nn])?[Kk]_).*"),
    UNIVERSAL("^(?<prefix>[RMG])_(?<abbreviation>[a-zA-Z0-9][a-zA-Z0-9_]+?)(?:_(?<compartment>[a-z][a-z0-9]?))?"
      + "(?:_(?<tissueCode>[A-Z][A-Z0-9]?))?$");

    /**
     *
     */
    private final Pattern pattern;

    /**
     * @param pattern
     */
    IDPattern(String pattern) {
      this.pattern = Pattern.compile(pattern);
    }


    /**
     * @return
     */
    Pattern get() {
      return pattern;
    }
  }

  /**
   *
   */
  public BiGGId() {
    super();
  }


  /**
   * @param id
   */
  public BiGGId(String id) {
    this();
    parseBiGGId(id);
  }


  /**
   * @param prefix
   * @param abbreviation
   * @param compartmentCode
   * @param tissueCode
   */
  public BiGGId(String prefix, String abbreviation, String compartmentCode, String tissueCode) {
    this();
    String id = toBiGGId(prefix, abbreviation, compartmentCode, tissueCode);
    parseBiGGId(id);
  }


  public static Optional<BiGGId> createMetaboliteId(String id) {
    return createMetaboliteId(id, true);
  }


  public static Optional<BiGGId> createMetaboliteId(String id, boolean correct) {
    if (id.isEmpty()) {
      return Optional.empty();
    }
    id = fixCompartmentCode(id);
    if (correct) {
      id = makeBiGGConform(id);
      if (id.startsWith("_")) {
        id = id.substring(1);
      }
      if (id.startsWith("m_")) {
        id = id.replaceAll("^m_", "M_");
      } else if (!id.startsWith("M_")) {
        id = "M_" + id;
      }
    }
    // handle one letter abbreviation metabolites like 'h' which are not in accord with the specification, but still
    // present in BiGG
    Matcher metaboliteSpecialCase = IDPattern.METABOLITE_SPECIAL.get().matcher(id);
    if (metaboliteSpecialCase.matches()) {
      BiGGId biggId = new BiGGId();
      biggId.setPrefix("M");
      biggId.setAbbreviation(metaboliteSpecialCase.group("abbreviation"));
      biggId.setCompartmentCode(metaboliteSpecialCase.group("compartment"));
      return Optional.of(biggId);
    } else {
      return Optional.of(new BiGGId(id));
    }
  }


  public static Optional<BiGGId> createGeneId(String id) {
    return createGeneId(id, true);
  }


  public static Optional<BiGGId> createGeneId(String id, boolean correct) {
    if (id.isEmpty()) {
      return Optional.empty();
    }
    if (correct) {
      id = makeBiGGConform(id);
      if (id.startsWith("_")) {
        id = id.substring(1);
      }
      if (id.startsWith("g_")) {
        id = id.replaceAll("^g_", "G_");
      } else if (!id.startsWith("G_")) {
        id = "G_" + id;
      }
    }
    return Optional.of(new BiGGId(id));
  }


  public static Optional<BiGGId> createReactionId(String id) {
    String prefixStripped = "";
    if (id.startsWith("R_") || id.startsWith("r_")) {
      prefixStripped = id.substring(2);
    }
    if (isPseudo(id)) {
      return createReactionId(id, true, true);
    } else if (!prefixStripped.isEmpty() && isPseudo(prefixStripped)) {
      return createReactionId(prefixStripped, true, true);
    } else {
      return createReactionId(id, true, false);
    }
  }


  private static boolean isPseudo(String reactionId) {
    return IDPattern.ATPM.get().matcher(reactionId).matches() || IDPattern.BIOMASS.get().matcher(reactionId).matches()
      || IDPattern.PSEUDO.get().matcher(reactionId).matches();
  }


  public static Optional<BiGGId> createReactionId(String id, boolean correct, boolean isPseudo) {
    if (id.isEmpty()) {
      return Optional.empty();
    }
    if (correct) {
      id = makeBiGGConform(id);
      if (id.startsWith("_")) {
        id = id.substring(1);
      }
      if (!isPseudo && id.startsWith("r_")) {
        id = id.replaceAll("^r_", "R_");
      } else if (!isPseudo && !id.startsWith("R_")) {
        id = "R_" + id;
      }
    }
    return Optional.of(new BiGGId(id));
  }


  private static String makeBiGGConform(String id) {
    if (Character.isDigit(id.charAt(0))) {
      id = "_" + id;
    }
    id = id.replaceAll("[-/]", "__").replaceAll("\\.", "__SBML_DOT__").replaceAll("\\(", "_LPAREN_")
           .replaceAll("\\)", "_RPAREN_").replaceAll("\\[", "_LBRACKET_").replaceAll("]", "_RBRACKET_");
    Pattern parenCompartment = Pattern.compile("_LPAREN_(?<paren>.*?)_RPAREN_");
    Matcher parenMatcher = parenCompartment.matcher(id);
    if (parenMatcher.find()) {
      id = id.replaceAll(parenCompartment.toString(), "_" + parenMatcher.group("paren"));
    }
    Pattern bracketCompartment = Pattern.compile("_LBRACKET_(?<bracket>.*)_RBRACKET_");
    Matcher bracketMatcher = bracketCompartment.matcher(id);
    if (bracketMatcher.find()) {
      id = id.replaceAll(bracketCompartment.toString(), "_" + bracketMatcher.group("bracket"));
    }
    if (id.matches(".*_copy\\d*")) {
      id = id.substring(0, id.lastIndexOf('_'));
    }
    Pattern alphaNum = Pattern.compile("[a-zA-Z0-9_]");
    StringBuilder builder = new StringBuilder(id.length());
    for (char ch : id.toCharArray()) {
      if (alphaNum.matcher(String.valueOf(ch)).matches()) {
        builder.append(ch);
      } else {
        builder.append("_");
      }
    }
    id = builder.toString();
    if (id.endsWith("_")) {
      id = id.substring(0, id.length() - 1);
    }
    return id;
  }


  public static boolean isValid(String queryId) {
    return IDPattern.ATPM.get().matcher(queryId).matches() || IDPattern.BIOMASS.get().matcher(queryId).matches()
      || IDPattern.COMPARTMENT.get().matcher(queryId).matches()
      || IDPattern.METABOLITE_SPECIAL.get().matcher(queryId).matches()
      || IDPattern.PSEUDO.get().matcher(queryId).matches() || IDPattern.UNIVERSAL.get().matcher(queryId).matches();
  }


  /**
   * @param id
   * @return
   */
  private static String fixCompartmentCode(String id) {
    // Workaround for models with wrong compartment code format [cc] instead of _cc
    Pattern rescueCompartment = Pattern.compile(".*\\[(?<code>[a-z][a-z0-9]?)\\]");
    Matcher rescueMatcher = rescueCompartment.matcher(id);
    if (rescueMatcher.matches()) {
      String compartmentCode = rescueMatcher.group("code");
      id = id.replaceAll("\\[[a-z][a-z0-9]?\\]", "_" + compartmentCode + "_");
      if (id.endsWith("_")) {
        id = id.substring(0, id.length() - 1);
      }
    }
    return id;
  }


  /**
   * @param id
   *        the identifier to be parsed into a bigg_id.
   */
  private void parseBiGGId(String id) {
    Matcher matcher = IDPattern.UNIVERSAL.get().matcher(id);
    // Handle PseudoReaction with wrongfully added prefix correctly
    boolean isPseudoReaction = false;
    if (id.startsWith("R_")) {
      isPseudoReaction = isPseudo(id);
    }
    if (!isPseudoReaction && matcher.matches()) {
      handleNormalId(matcher);
    } else {
      handleSpecialCases(id);
    }
  }


  /**
   * @param matcher
   */
  private void handleNormalId(Matcher matcher) {
    String prefix = matcher.group("prefix");
    setPrefix(prefix);
    String abbreviation = matcher.group("abbreviation");
    setAbbreviation(abbreviation);
    String compartment = matcher.group("compartment");
    setCompartmentCode(compartment);
    String tissueCode = matcher.group("tissueCode");
    setTissueCode(tissueCode);
  }


  /**
   * @param id
   */
  private void handleSpecialCases(String id) {
    Matcher pseudoreactionMatcher = IDPattern.PSEUDO.get().matcher(id);
    Matcher biomassMatcher = IDPattern.BIOMASS.get().matcher(id);
    Matcher atpmMatcher = IDPattern.ATPM.get().matcher(id);
    Matcher compartmentMatcher = IDPattern.COMPARTMENT.get().matcher(id);
    if (pseudoreactionMatcher.matches()) {
      id = id.replaceAll("^([Rr]_?)?[Ee][Xx]", "EX");
      id = id.replaceAll("^([Rr]_?)?[Dd][Mm]", "DM");
      id = id.replaceAll("^([Rr]_?)?[Ss]([Ii][Nn])?[Kk]", "SK");
      setAbbreviation(id);
    } else if (biomassMatcher.matches()) {
      id = id.replaceAll("^([Rr]_?)?[Bb][Ii][Oo][Mm][Aa][Ss][Ss]", "BIOMASS");
      setAbbreviation(id);
    } else if (atpmMatcher.matches()) {
      setAbbreviation("ATPM");
    } else if (compartmentMatcher.matches()) {
      setAbbreviation(id);
    } else {
      logger.warning(format("Cannot convert to BiGGId, setting as abbreviation: {0}", id));
      setAbbreviation(id);
    }
  }


  public static Optional<String> extractCompartmentCode(String id) {
    if (!Pattern.compile("(C_)?[a-z][a-z0-9]?").matcher(id).matches()) {
      return Optional.empty();
    }
    if (id.startsWith("C_")) {
      id = id.substring(2);
    }
    return Optional.of(id);
  }


  /*
   * (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    BiGGId other = (BiGGId) obj;
    if (abbreviation == null) {
      if (other.abbreviation != null) {
        return false;
      }
    } else if (!abbreviation.equals(other.abbreviation)) {
      return false;
    }
    if (compartmentCode == null) {
      if (other.compartmentCode != null) {
        return false;
      }
    } else if (!compartmentCode.equals(other.compartmentCode)) {
      return false;
    }
    if (prefix == null) {
      if (other.prefix != null) {
        return false;
      }
    } else if (!prefix.equals(other.prefix)) {
      return false;
    }
    if (tissueCode == null) {
      return other.tissueCode == null;
    } else
      return tissueCode.equals(other.tissueCode);
  }


  /**
   * @return the abbreviation
   */
  public String getAbbreviation() {
    return isSetAbbreviation() ? abbreviation : "";
  }


  /**
   * <ul>
   * <li>Only contain upper and lower case letters, numbers, and underscores
   * <li>/[0-9a-zA-Z][a-zA-Z0-9_]+/, only ASCII and don't start with numbers
   * <li>When converting old BIGG IDs to BIGG2 IDs, replace a dash with two
   * underscores. For example, ala-L becomes ala__L.
   * <li>Reactions should be all upper case. Metabolites should be primarily
   * lower case, but upper case letters are allowed (ala__L is preferred to
   * ALA__L).
   * </ul>
   *
   * @param abbreviation
   *        the abbreviation to set
   */
  public void setAbbreviation(String abbreviation) {
    if (null != abbreviation && !abbreviation.isEmpty()) {
      this.abbreviation = abbreviation;
    }
  }


  /**
   * @return the compartmentCode
   */
  public String getCompartmentCode() {
    return isSetCompartmentCode() ? compartmentCode : "";
  }


  /**
   * One or two characters in length, and contain only lower case letters and
   * numbers, and must begin with a lower case letter. /[a-z][a-z0-9]?/
   *
   * @param compartmentCode
   *        the compartmentCode to set
   */
  public void setCompartmentCode(String compartmentCode) {
    if (null != compartmentCode && !compartmentCode.isEmpty()) {
      this.compartmentCode = compartmentCode;
    }
  }


  /**
   * @return the prefix
   */
  public String getPrefix() {
    return isSetPrefix() ? prefix : "";
  }


  /**
   * <ul>
   * <li>R: reaction
   * <li>M: metabolite /[RM]/
   * <li>NOTE: Do we want to have the id entity use R and M, and just remove
   * them when constructing the model, or have them just as
   * [abbreviation]_[compartment code] and add the prefix when they are put
   * into SBML models? Also SBML id's use capital letters (/[RM]/).
   * </ul>
   *
   * @param prefix
   *        the prefix to set
   */
  public void setPrefix(String prefix) {
    if (null != prefix && !prefix.isEmpty()) {
      this.prefix = prefix;
    }
  }


  /**
   * @return the tissueCode
   */
  public String getTissueCode() {
    return isSetTissueCode() ? tissueCode : "";
  }


  /**
   * One or two characters in length, and contain only upper case letters and
   * numbers, and must begin with an upper case letter. /[A-Z][A-Z0-9]?/
   *
   * @param tissueCode
   *        the tissueCode to set
   */
  public void setTissueCode(String tissueCode) {
    if (null != tissueCode && !tissueCode.isEmpty()) {
      this.tissueCode = tissueCode;
    }
  }


  /*
   * (non-Javadoc)
   * @see java.lang.Object#hashCode()
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((abbreviation == null) ? 0 : abbreviation.hashCode());
    result = prime * result + ((compartmentCode == null) ? 0 : compartmentCode.hashCode());
    result = prime * result + ((prefix == null) ? 0 : prefix.hashCode());
    result = prime * result + ((tissueCode == null) ? 0 : tissueCode.hashCode());
    return result;
  }


  /**
   * @return
   */
  public boolean isSetAbbreviation() {
    return abbreviation != null;
  }


  /**
   * @return
   */
  public boolean isSetCompartmentCode() {
    return compartmentCode != null;
  }


  /**
   * @return
   */
  public boolean isSetPrefix() {
    return prefix != null;
  }


  /**
   * @return
   */
  public boolean isSetTissueCode() {
    return tissueCode != null;
  }


  /**
   * Generates an actual BiGG id for this object.
   *
   * @return
   */
  public String toBiGGId() {
    StringBuilder sb = new StringBuilder();
    if (isSetPrefix()) {
      sb.append(getPrefix());
    }
    if (isSetAbbreviation()) {
      if (sb.length() > 0) {
        sb.append('_');
      }
      sb.append(getAbbreviation());
    }
    if (isSetCompartmentCode()) {
      if (sb.length() > 0) {
        sb.append('_');
      }
      sb.append(getCompartmentCode());
    }
    if (isSetTissueCode()) {
      if (sb.length() > 0) {
        sb.append('_');
      }
      sb.append(getTissueCode());
    }
    return sb.toString();
  }


  /**
   * Generates an actual BiGG id for this object.
   *
   * @return
   */
  public String toBiGGId(String prefix, String abbreviation, String compartmentCode, String tissueCode) {
    StringBuilder sb = new StringBuilder();
    if (!Optional.ofNullable(prefix).orElse("").isEmpty()) {
      sb.append(prefix);
    }
    if (!Optional.ofNullable(abbreviation).orElse("").isEmpty()) {
      if (sb.length() > 0) {
        sb.append('_');
      }
      sb.append(abbreviation);
    }
    if (!Optional.ofNullable(compartmentCode).orElse("").isEmpty()) {
      if (sb.length() > 0) {
        sb.append('_');
      }
      sb.append(compartmentCode);
    }
    if (!Optional.ofNullable(tissueCode).orElse("").isEmpty()) {
      if (sb.length() > 0) {
        sb.append('_');
      }
      sb.append(tissueCode);
    }
    return sb.toString();
  }


  /*
   * (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return getClass().getSimpleName() + " [prefix=" + prefix + ", abbreviation=" + abbreviation + ", compartmentCode="
      + compartmentCode + ", tissueCode=" + tissueCode + "]";
  }
}
