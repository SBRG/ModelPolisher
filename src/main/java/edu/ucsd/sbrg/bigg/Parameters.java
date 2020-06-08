package edu.ucsd.sbrg.bigg;

import de.zbit.util.ResourceManager;
import de.zbit.util.prefs.Option;
import de.zbit.util.prefs.SBProperties;

import java.io.File;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Helper class to store all parameters for running ModelPolisher in batch
 * mode.
 *
 * @author Andreas Dr&auml;ger
 */
public class Parameters {

  /**
   * {@link Logger} for this class
   */
  private static final transient Logger logger = Logger.getLogger(Parameters.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * Singleton for ModelPolisher parameters
   */
  static Parameters parameters;
  /**
   * @see ModelPolisherOptions#INCLUDE_ANY_URI
   */
  private Boolean includeAnyURI = null;
  /**
   * @see ModelPolisherOptions#ANNOTATE_WITH_BIGG
   */
  private Boolean annotateWithBiGG = null;
  /**
   * @see ModelPolisherOptions#OUTPUT_COMBINE
   */
  private Boolean outputCOMBINE = null;
  /**
   * @see ModelPolisherOptions#ADD_ADB_ANNOTATIONS
   */
  private Boolean addADBAnnotations = null;
  /**
   * @see ModelPolisherOptions#CHECK_MASS_BALANCE
   */
  private Boolean checkMassBalance = null;
  /**
   * @see ModelPolisherOptions#NO_MODEL_NOTES
   */
  private Boolean noModelNotes = null;
  /**
   * @see ModelPolisherOptions#COMPRESSION_TYPE
   */
  private ModelPolisherOptions.Compression compression = ModelPolisherOptions.Compression.NONE;
  /**
   * Can be {@code null}
   *
   * @see ModelPolisherOptions#DOCUMENT_NOTES_FILE
   */
  private File documentNotesFile = null;
  /**
   * Can be {@code null} (then a default is used).
   *
   * @see ModelPolisherOptions#DOCUMENT_TITLE_PATTERN
   */
  private String documentTitlePattern = null;
  /**
   * @see ModelPolisherOptions#FLUX_COEFFICIENTS
   */
  double[] fluxCoefficients = null;
  /**
   * @see ModelPolisherOptions#FLUX_OBJECTIVES
   */
  private String[] fluxObjectives = null;
  /**
   * Can be {@code null}
   *
   * @see ModelPolisherOptions#MODEL_NOTES_FILE
   */
  private File modelNotesFile = null;
  /**
   * @see ModelPolisherOptions#OMIT_GENERIC_TERMS
   */
  private Boolean omitGenericTerms = null;
  /**
   * @see ModelPolisherOptions#SBML_VALIDATION
   */
  private Boolean sbmlValidation = null;
  /**
   * @see IOOptions#INPUT
   */
  private File input = null;
  /**
   * @see IOOptions#OUTPUT
   */
  private File output = null;

  /**
   * 
   */
  private Parameters(SBProperties args) throws IllegalArgumentException {
    super();
    initParameters(args);
  }


  /**
   * 
   */
  static Parameters init(SBProperties args) throws IllegalArgumentException {
    if (parameters == null) {
      parameters = new Parameters(args);
    }
    return parameters;
  }


  public static Parameters get() {
    if (parameters != null) {
      return parameters;
    } else {
      // this should not happen, abort
      throw new IllegalStateException("Parameters not initialized");
    }
  }


  /**
   * @param args:
   *        Arguments from commandline
   */
  private void initParameters(SBProperties args) throws IllegalArgumentException {
    String inPath = args.getProperty(IOOptions.INPUT);
    if (inPath == null) {
      throw new IllegalArgumentException("--input is missing, but needs to be provided, aborting.");
    }
    input = new File(inPath);
    String outPath = args.getProperty(IOOptions.OUTPUT);
    if (outPath == null) {
      throw new IllegalArgumentException("--output is missing, but needs to be provided, aborting.");
    }
    output = new File(outPath);
    documentTitlePattern = args.getProperty(ModelPolisherOptions.DOCUMENT_TITLE_PATTERN);
    if (args.containsKey(ModelPolisherOptions.FLUX_COEFFICIENTS)) {
      String c = args.getProperty(ModelPolisherOptions.FLUX_COEFFICIENTS);
      String[] coeff = c.substring(1, c.length() - 1).split(",");
      fluxCoefficients = new double[coeff.length];
      for (int i = 0; i < coeff.length; i++) {
        fluxCoefficients[i] = Double.parseDouble(coeff[i].trim());
      }
    }
    if (args.containsKey(ModelPolisherOptions.FLUX_OBJECTIVES)) {
      String fObjectives = args.getProperty(ModelPolisherOptions.FLUX_OBJECTIVES);
      fluxObjectives = fObjectives.substring(1, fObjectives.length() - 1).split(":");
    }
    annotateWithBiGG = args.getBooleanProperty(ModelPolisherOptions.ANNOTATE_WITH_BIGG);
    outputCOMBINE = args.getBooleanProperty(ModelPolisherOptions.OUTPUT_COMBINE);
    addADBAnnotations = args.getBooleanProperty(ModelPolisherOptions.ADD_ADB_ANNOTATIONS);
    checkMassBalance = args.getBooleanProperty(ModelPolisherOptions.CHECK_MASS_BALANCE);
    noModelNotes = args.getBooleanProperty(ModelPolisherOptions.NO_MODEL_NOTES);
    compression = ModelPolisherOptions.Compression.valueOf(args.getProperty(ModelPolisherOptions.COMPRESSION_TYPE));
    documentNotesFile = parseFileOption(args, ModelPolisherOptions.DOCUMENT_NOTES_FILE);
    includeAnyURI = args.getBooleanProperty(ModelPolisherOptions.INCLUDE_ANY_URI);
    modelNotesFile = parseFileOption(args, ModelPolisherOptions.MODEL_NOTES_FILE);
    omitGenericTerms = args.getBooleanProperty(ModelPolisherOptions.OMIT_GENERIC_TERMS);
    sbmlValidation = args.getBooleanProperty(ModelPolisherOptions.SBML_VALIDATION);
  }


  /**
   * Scans the given command-line options for a specific file option and
   * returns the corresponding file if it exists, {@code null} otherwise.
   *
   * @param args
   *        command-line options.
   * @param option
   *        a specific file option to look for.
   * @return a {@link File} object that corresponds to a desired command-line
   *         option, or {@code null} if it does not exist.
   */
  private File parseFileOption(SBProperties args, Option<File> option) {
    if (args.containsKey(option)) {
      File notesFile = new File(args.getProperty(option));
      if (notesFile.exists() && notesFile.canRead()) {
        return notesFile;
      }
    }
    return null;
  }


  public Boolean includeAnyURI() {
    return includeAnyURI;
  }


  public Boolean annotateWithBiGG() {
    return annotateWithBiGG;
  }


  public Boolean outputCOMBINE() {
    return outputCOMBINE;
  }


  public Boolean addADBAnnotations() {
    return addADBAnnotations;
  }


  public Boolean checkMassBalance() {
    return checkMassBalance;
  }


  public Boolean noModelNotes() {
    return noModelNotes;
  }


  public ModelPolisherOptions.Compression compression() {
    return compression;
  }


  public File documentNotesFile() {
    return documentNotesFile;
  }


  public String documentTitlePattern() {
    return documentTitlePattern;
  }


  public double[] fluxCoefficients() {
    return fluxCoefficients;
  }


  public String[] fluxObjectives() {
    return fluxObjectives;
  }


  public File modelNotesFile() {
    return modelNotesFile;
  }


  public Boolean omitGenericTerms() {
    return omitGenericTerms;
  }


  public Boolean SBMLValidation() {
    return sbmlValidation;
  }


  public File input() {
    return input;
  }


  public File output() {
    return output;
  }
}
