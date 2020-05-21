package edu.ucsd.sbrg.parsers;

import us.hebi.matlab.mat.types.Array;
import us.hebi.matlab.mat.types.Cell;
import us.hebi.matlab.mat.types.Char;
import us.hebi.matlab.mat.types.MatlabType;
import us.hebi.matlab.mat.types.Matrix;
import us.hebi.matlab.mat.types.Sparse;
import us.hebi.matlab.mat.types.Struct;

import java.util.logging.Logger;

import static edu.ucsd.sbrg.bigg.ModelPolisher.MESSAGES;
import static java.text.MessageFormat.format;

/**
 *
 */
class MatlabFields {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(MatlabFields.class.getName());
  // fields
  Array description;
  Cell author;
  Cell citations;
  Cell comments;
  Cell confidenceScores;
  Cell ecNumbers;
  Cell genedate;
  Cell geneindex;
  Cell genesource;
  Cell genes;
  Cell grRules;
  Cell metCHEBIID;
  Cell metFormulas;
  Cell metHMDB;
  Cell metInchiString;
  Cell metKeggID;
  Cell metNames;
  Cell metPubChemID;
  Cell metSmile;
  Cell mets;
  Cell name;
  Cell notes;
  Cell organism;
  Cell rxns;
  Cell rxnCOG;
  Cell rxnKeggID;
  Cell rxnKeggOrthology;
  Cell rxnNames;
  Cell subSystems;
  Char csense;
  Matrix lb; // Double
  Matrix metCharge; // Double
  Matrix ub; // Double
  Matrix b; // NumericArray<?>
  Matrix coefficients; // NumericArray<?>
  Matrix rev; // NumericArray<?>
  Sparse S;
  Struct struct;

  /**
   * @param struct
   */
  MatlabFields(Struct struct) {
    this.struct = struct;
    initializeFields();
  }


  /**
   *
   */
  void initializeFields() {
    b = toNumericArrayMatrix(getStructField(ModelField.b));
    citations = toCell(getStructField(ModelField.citations), ModelField.citations.name());
    coefficients = toNumericArrayMatrix(getStructField(ModelField.c));
    comments = toCell(getStructField(ModelField.comments), ModelField.comments.name());
    confidenceScores = toCell(getStructField(ModelField.confidenceScores), ModelField.confidenceScores.name());
    description = toCell(getStructField(ModelField.description), ModelField.description.name());
    csense = toChar(getStructField(ModelField.csense), ModelField.csense.name());
    ecNumbers = toCell(getStructField(ModelField.ecNumbers), ModelField.ecNumbers.name());
    genes = toCell(getStructField(ModelField.mets), ModelField.mets.name());
    grRules = toCell(getStructField(ModelField.grRules), ModelField.grRules.name());
    lb = toDouble(getStructField(ModelField.lb), ModelField.lb.name());
    mets = toCell(getStructField(ModelField.mets), ModelField.mets.name());
    metCharge = toDouble(getStructField(ModelField.metCharge), ModelField.metCharge.name());
    metCHEBIID = toCell(getStructField(ModelField.metCHEBIID), ModelField.metCHEBIID.name());
    metFormulas = toCell(getStructField(ModelField.metFormulas), ModelField.metFormulas.name());
    metHMDB = toCell(getStructField(ModelField.metHMDB), ModelField.metHMDB.name());
    metInchiString = toCell(getStructField(ModelField.metInchiString), ModelField.metInchiString.name());
    metKeggID = toCell(getStructField(ModelField.metKeggID), ModelField.metKeggID.name());
    metNames = toCell(getStructField(ModelField.metNames), ModelField.metNames.name());
    metPubChemID = toCell(getStructField(ModelField.metPubChemID), ModelField.metPubChemID.name());
    metSmile = toCell(getStructField(ModelField.metSmile), ModelField.metSmile.name());
    rev = toNumericArrayMatrix(getStructField(ModelField.rev));
    rxns = toCell(getStructField(ModelField.rxns), ModelField.rxns.name());
    rxnCOG = toCell(getStructField(ModelField.rxnCOG), ModelField.rxnCOG.name());
    rxnKeggID = toCell(getStructField(ModelField.rxnKeggID), ModelField.rxnKeggID.name());
    rxnKeggOrthology = toCell(getStructField(ModelField.rxnKeggOrthology), ModelField.rxnKeggOrthology.name());
    rxnNames = toCell(getStructField(ModelField.rxnNames), ModelField.rxnNames.name());
    S = toSparse(getStructField(ModelField.S), ModelField.S.name());
    subSystems = toCell(getStructField(ModelField.subSystems), ModelField.subSystems.name());
    ub = toDouble(getStructField(ModelField.ub), ModelField.ub.name());
  }


  /**
   *
   */
  void setDescriptionFields() {
    Struct descrStruct = (Struct) description;
    name = toCell(getStructField(descrStruct, ModelField.name), ModelField.name.name());
    organism = toCell(getStructField(descrStruct, ModelField.organism), ModelField.organism.name());
    author = toCell(getStructField(descrStruct, ModelField.author), ModelField.author.name());
    geneindex = toCell(getStructField(descrStruct, ModelField.geneindex), ModelField.geneindex.name());
    genedate = toCell(getStructField(descrStruct, ModelField.genedate), ModelField.genedate.name());
    genesource = toCell(getStructField(descrStruct, ModelField.genesource), ModelField.genesource.name());
    notes = toCell(getStructField(descrStruct, ModelField.notes), ModelField.notes.name());
  }


  /**
   * @param field
   * @return
   */
  private Array getStructField(ModelField field) {
    return getStructField(this.struct, field.name());
  }


  /**
   * @param struct
   * @param field
   * @return
   */
  private Array getStructField(Struct struct, ModelField field) {
    return getStructField(struct, field.name());
  }


  /**
   * @param struct
   * @param fieldName
   * @return Array
   */
  static Array getStructField(Struct struct, String fieldName) {
    try {
      return struct.get(fieldName);
    } catch (Exception e) {
      logger.info(format(MESSAGES.getString("STRUCT_FIELD_NOT_PRESENT"), fieldName, e.toString()));
      return null;
    }
  }


  /**
   * @param array
   * @return
   */
  private Cell toCell(Array array, String arrayName) {
    if (array != null) {
      if (array.getType() == MatlabType.Cell) {
        return (Cell) array;
      }
      logger.warning(format(MESSAGES.getString("TYPE_MISMATCH_CELL"), array.getType().toString(), arrayName));
    }
    return null;
  }


  /**
   * @param array
   * @return
   */
  private Char toChar(Array array, String arrayName) {
    if (array != null) {
      if (array.getType() == MatlabType.Character) {
        return (Char) array;
      }
      logger.warning(format(MESSAGES.getString("TYPE_MISMATCH_CHAR"), array.getType().toString(), arrayName));
    }
    return null;
  }


  /**
   * @param array
   * @return
   */
  private Matrix toDouble(Array array, String arrayName) {
    if (array != null) {
      if (array.getType() == MatlabType.Double) {
        return (Matrix) array;
      }
      logger.warning(format(MESSAGES.getString("TYPE_MISMATCH_DOUBLE"), array.getType().toString(), arrayName));
    }
    return null;
  }


  /**
   * @param array
   * @return
   */
  private Matrix toNumericArrayMatrix(Array array) {
    if (array instanceof Matrix) {
      return (Matrix) array;
    }
    return null;
  }


  /**
   * @param array
   * @return
   */
  private Sparse toSparse(Array array, String arrayName) {
    if (array.getType() == MatlabType.Sparse) {
      return (Sparse) array;
    }
    logger.warning(format(MESSAGES.getString("TYPE_MISMATCH_S_ARRAY"), array.getType().toString(), arrayName));
    return null;
  }
}
