package edu.ucsd.sbrg.bigg;

import java.io.File;
import java.util.ResourceBundle;

import de.zbit.util.ResourceManager;
import de.zbit.util.prefs.Option;
import de.zbit.util.prefs.SBProperties;

/**
 * Helper class to store all parameters for running ModelPolisher in batch
 * mode.
 *
 * @author Andreas Dr&auml;ger
 */
public class Parameters {

  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * Singleton for ModelPolisher parameters
   */
  static Parameters parameters;
  /**
   * @see ModelPolisherOptions#ADD_ADB_ANNOTATIONS
   */
  private boolean addADBAnnotations = ModelPolisherOptions.ADD_ADB_ANNOTATIONS.getDefaultValue();
  /**
   * @see ModelPolisherOptions#ANNOTATE_WITH_BIGG
   */
  private boolean annotateWithBiGG = ModelPolisherOptions.ANNOTATE_WITH_BIGG.getDefaultValue();
  /**
   * @see ModelPolisherOptions#CHECK_MASS_BALANCE
   */
  private boolean checkMassBalance = ModelPolisherOptions.CHECK_MASS_BALANCE.getDefaultValue();
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
   * @see ModelPolisherOptions#DOCUMENT_TITLE_PATTERN
   */
  private String documentTitlePattern = ModelPolisherOptions.DOCUMENT_TITLE_PATTERN.getDefaultValue();
  /**
   * @see ModelPolisherOptions#FLUX_COEFFICIENTS
   */
  // default is a boxed value, i.e. easier to just set it explicitly here to the same default value
  double[] fluxCoefficients = new double[0];
  /**
   * @see ModelPolisherOptions#FLUX_OBJECTIVES
   */
  private String[] fluxObjectives = ModelPolisherOptions.FLUX_OBJECTIVES.getDefaultValue();
  /**
   * @see ModelPolisherOptions#INCLUDE_ANY_URI
   */
  private boolean includeAnyURI = ModelPolisherOptions.INCLUDE_ANY_URI.getDefaultValue();
  /**
   * Can be {@code null}
   *
   * @see ModelPolisherOptions#MODEL_NOTES_FILE
   */
  private File modelNotesFile = null;
  /**
   * @see ModelPolisherOptions#NO_MODEL_NOTES
   */
  private boolean noModelNotes = ModelPolisherOptions.NO_MODEL_NOTES.getDefaultValue();
  /**
   * @see ModelPolisherOptions#OMIT_GENERIC_TERMS
   */
  private boolean omitGenericTerms = ModelPolisherOptions.OMIT_GENERIC_TERMS.getDefaultValue();
  /**
   * @see ModelPolisherOptions#OUTPUT_COMBINE
   */
  private boolean outputCOMBINE = ModelPolisherOptions.OUTPUT_COMBINE.getDefaultValue();
  /**
   * @see ModelPolisherOptions#SBML_VALIDATION
   */
  private boolean sbmlValidation = ModelPolisherOptions.SBML_VALIDATION.getDefaultValue();
  /**
   * @see IOOptions#INPUT
   */
  private File input = null;
  /**
   * @see IOOptions#OUTPUT
   */
  private File output = null;

  /**
   * Default constructor for testing purposes
   */
  private Parameters() {
    super();
  }


  /**
   * Constructor for non testing code path
   * 
   * @param args
   *        SBProperties from {@link Parameters#init(SBProperties)}
   * @throws IllegalArgumentException
   *         propagated from {@link Parameters#initParameters(SBProperties)}
   */
  private Parameters(SBProperties args) throws IllegalArgumentException {
    super();
    initParameters(args);
  }


  /**
   * Initializes parameters from commandline arguments, if they are not yet present, else simply returns the initialized
   * instance.
   * Prefer {@link Parameters#get()} to get an initialized instance
   *
   * @param args
   *        {@link SBProperties} file with commandline arguments stored
   * @return Initialized {@link Parameters}
   * @throws IllegalArgumentException
   *         propagated from {@link Parameters(SBProperties)}
   */
  static Parameters init(SBProperties args) throws IllegalArgumentException {
    if (parameters == null) {
      parameters = new Parameters(args);
    }
    return parameters;
  }


  /**
   * Returns initialized {@link Parameters} instance. Throws {@link IllegalStateException} if Parameters have not been
   * initialized
   *
   * @return Initialized {@link Parameters} instance
   */
  public static Parameters get() {
    if (parameters != null) {
      return parameters;
    } else {
      // this should not happen, abort
      throw new IllegalStateException(MESSAGES.getString("PARAM_STATE_INVALID"));
    }
  }


  /**
   * @return Parameter set usable for testing, initialized with defaults, all {@link File}s are {@code null}
   */
  public static Parameters initDefaults() {
    return new Parameters();
  }


  /**
   * Converts {@link SBProperties} holding commandline arguments into usable {@link Parameters}
   *
   * @param args:
   *        Arguments from commandline
   * @throws IllegalArgumentException
   *         if either input or output file are not provided, as program execution makes no sense if either is missing
   */
  private void initParameters(SBProperties args) throws IllegalArgumentException {
    String inPath = args.getProperty(IOOptions.INPUT);
    if (inPath == null) {
      throw new IllegalArgumentException(MESSAGES.getString("PARAM_INPUT_MISSING"));
    }
    input = new File(inPath);
    String outPath = args.getProperty(IOOptions.OUTPUT);
    if (outPath == null) {
      throw new IllegalArgumentException(MESSAGES.getString("PARAM_OUTPUT_MISSING"));
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



  public boolean includeAnyURI() {
    return includeAnyURI;
  }


  public boolean annotateWithBiGG() {
    return annotateWithBiGG;
  }


  public boolean outputCOMBINE() {
    return outputCOMBINE;
  }


  public boolean addADBAnnotations() {
    return addADBAnnotations;
  }


  public boolean checkMassBalance() {
    return checkMassBalance;
  }


  public boolean noModelNotes() {
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


  public boolean omitGenericTerms() {
    return omitGenericTerms;
  }


  public boolean SBMLValidation() {
    return sbmlValidation;
  }


  public File input() {
    return input;
  }


  public File output() {
    return output;
  }
}
