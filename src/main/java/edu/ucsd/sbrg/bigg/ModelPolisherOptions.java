/*
 * 
 */
package edu.ucsd.sbrg.bigg;

import de.zbit.util.ResourceManager;
import de.zbit.util.prefs.KeyProvider;
import de.zbit.util.prefs.Option;

import java.io.File;
import java.util.ResourceBundle;

/**
 * @author Andreas Dr&auml;ger
 */
public interface ModelPolisherOptions extends KeyProvider {

  /**
   * Bundle for ModelPolisher logger messages
   */
  ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");

  /**
   * @author Andreas Dr&auml;ger
   */
  enum Compression {

    /**
     * 
     */
    GZIP("gz"),
    /**
     * 
     */
    NONE,
    /**
     * 
     */
    ZIP("zip");

    /**
     * 
     */
    private final String extension;

    /**
     * 
     */
    Compression() {
      this(null);
    }


    /**
     * @param extension
     */
    Compression(String extension) {
      this.extension = extension;
    }


    /**
     * @return
     */
    public String getFileExtension() {
      return extension;
    }
  }

  /**
   * This switch allows users to specify if also those database cross-links
   * should be extracted from BiGG Models database for which currently no entry
   * in the MIRIAM exists. If set to true, ModelPolisher also includes URIs that
   * do not contain the pattern identifiers.org.
   */
  @SuppressWarnings("unchecked")
  Option<Boolean> INCLUDE_ANY_URI =
    new Option<>("INCLUDE_ANY_URI", Boolean.class, MESSAGES.getString("INCLUDE_ANY_URI_DESC"), Boolean.FALSE);
  /**
   * If set to true, the model will be annotated with data from BiGG Models
   * database. If set to false, the resulting model will not receive annotation
   * or correction from BiGG Models database
   */
  @SuppressWarnings("unchecked")
  Option<Boolean> ANNOTATE_WITH_BIGG =
    new Option<>("ANNOTATE_WITH_BIGG", Boolean.class, MESSAGES.getString("ANNOTATE_WITH_BIGG_DESC"), Boolean.FALSE);
  /**
   * If set to true, annotations will be added to species and reactions from AnnotateDB also.
   */
  @SuppressWarnings("unchecked")
  Option<Boolean> ADD_ADB_ANNOTATIONS =
    new Option<>("ADD_ADB_ANNOTATIONS", Boolean.class, MESSAGES.getString("ADD_ADB_ANNOTATIONS_DESC"), Boolean.FALSE);
  /**
   * If set to true, no web content will be inserted in the SBML container nor
   * into the model within the SBML file.
   */
  @SuppressWarnings("unchecked")
  Option<Boolean> NO_MODEL_NOTES =
    new Option<>("NO_MODEL_NOTES", Boolean.class, MESSAGES.getString("NO_MODEL_NOTES"), Boolean.FALSE);
  /**
   * When set to true, the mass balance of each reaction will be checked where
   * possible. Reactions that are recognized as peudoreactions are excluded from
   * this check, also are reactions that lack information about elementary
   * composition of their participants.
   */
  @SuppressWarnings("unchecked")
  Option<Boolean> CHECK_MASS_BALANCE =
    new Option<>("CHECK_MASS_BALANCE", Boolean.class, MESSAGES.getString("CHECK_MASS_BALANCE_DESC"), Boolean.TRUE);
  /**
   * 
   */
  @SuppressWarnings("unchecked")
  Option<Double[]> FLUX_COEFFICIENTS =
    new Option<>("FLUX_COEFFICIENTS", Double[].class, MESSAGES.getString("FLUX_COEFF_DESC"), new Double[0]);
  /**
   * 
   */
  @SuppressWarnings("unchecked")
  Option<String[]> FLUX_OBJECTIVES =
    new Option<>("FLUX_OBJECTIVES", String[].class, MESSAGES.getString("FLUX_OBJ_DESC"), new String[0]);
  /**
   * Decides whether or not the output file should directly be compressed and if
   * so, which archive type should be used.
   */
  @SuppressWarnings("unchecked")
  Option<Compression> COMPRESSION_TYPE =
    new Option<>("COMPRESSION_TYPE", Compression.class, MESSAGES.getString("COMPR_DESC"), Compression.NONE);
  /**
   * This option allows you to define the title of the SBML document's
   * description and hence the head line when the file is displayed in a web
   * browser.
   */
  @SuppressWarnings("unchecked")
  Option<String> DOCUMENT_TITLE_PATTERN = new Option<>("DOCUMENT_TITLE_PATTERN", String.class,
    MESSAGES.getString("DOC_TITLE_PATTERN_DESC"), "[biggId] - [organism]");
  /**
   * This XHTML file defines alternative model notes and makes them
   * exchangeable.
   */
  Option<File> MODEL_NOTES_FILE = new Option<>("MODEL_NOTES_FILE", File.class, MESSAGES.getString("MODEL_NOTES_DESC"));
  /**
   * This XHTML file defines alternative document notes and makes them
   * exchangeable.
   */
  Option<File> DOCUMENT_NOTES_FILE =
    new Option<>("DOCUMENT_NOTES_FILE", File.class, MESSAGES.getString("DOC_NOTES_DESC"));
  /**
   * Set this option to true if generic top-level annotations, such as 'process'
   * should not be applied. Not using those terms will reduce the size of the
   * resulting output file.
   */
  @SuppressWarnings("unchecked")
  Option<Boolean> OMIT_GENERIC_TERMS =
    new Option<>("OMIT_GENERIC_TERMS", Boolean.class, MESSAGES.getString("OMIT_GENERIC_TERMS_DESC"), Boolean.FALSE);
  /**
   * Produce output as a single COMBINE Archive.
   */
  @SuppressWarnings("unchecked")
  Option<Boolean> OUTPUT_COMBINE =
    new Option<>("OUTPUT_COMBINE", Boolean.class, MESSAGES.getString("OUTPUT_COMBINE"), Boolean.FALSE);
  /**
   * If true, the created SBML file will be validated through the online
   * validator service at {@link "http://sbml.org"}. This option is only used
   * if the output is GZIP compressed.
   */
  @SuppressWarnings("unchecked")
  Option<Boolean> SBML_VALIDATION =
    new Option<>("SBML_VALIDATION", Boolean.class, MESSAGES.getString("SBML_VAL_DESC"), Boolean.FALSE);
}
