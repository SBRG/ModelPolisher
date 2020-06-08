package edu.ucsd.sbrg.parsers.cobra;

import de.zbit.sbml.util.SBMLtools;
import de.zbit.util.ResourceManager;
import org.sbml.jsbml.Model;
import us.hebi.matlab.mat.types.Array;
import us.hebi.matlab.mat.types.Cell;
import us.hebi.matlab.mat.types.Char;
import us.hebi.matlab.mat.types.MatlabType;
import us.hebi.matlab.mat.types.Matrix;
import us.hebi.matlab.mat.types.Sparse;
import us.hebi.matlab.mat.types.Struct;

import javax.xml.stream.XMLStreamException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;

/**
 *
 */
class MatlabFields {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(MatlabFields.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  private final Map<String, Array> fields;
  private static MatlabFields instance;

  /**
   * @param struct
   */
  private MatlabFields(Struct struct) {
    fields = new HashMap<>();
    initializeFields(struct);
  }


  /**
   * @param struct
   */
  public static void init(Struct struct) {
    instance = new MatlabFields(struct);
  }


  /**
   *
   */
  private void initializeFields(Struct struct) {
    List<String> knownFields = Arrays.stream(ModelField.values()).map(Enum::name).collect(Collectors.toList());
    List<String> fieldsFound = struct.getFieldNames();
    logger.info(format("Known fields missing in this model: {0}",
      knownFields.parallelStream().filter(Predicate.not(fieldsFound::contains)).collect(Collectors.toSet())));
    logger.info(format("Found additional, unknown fields: {0}",
      fieldsFound.parallelStream().filter(Predicate.not(knownFields::contains)).collect(Collectors.toSet())));
    for (String field : fieldsFound) {
      fields.put(field, struct.get(field));
    }
  }


  /**
   * @param model
   */
  public void setDescriptionFields(Model model) {
    if (!fields.containsKey(ModelField.description.name())) {
      logger.info(format(MESSAGES.getString("FIELD_MISSING"), ModelField.description));
      return;
    }
    Array description = fields.get(ModelField.description.name());
    if (description.getType() == MatlabType.Character) {
      Char desc = (Char) description;
      if (desc.getDimensions()[0] == 1) {
        model.setName(desc.getRow(0));
      } else {
        logger.warning(format(MESSAGES.getString("MANY_IDS_IN_DESC"), desc.asCharSequence()));
      }
    } else if (description.getType() == MatlabType.Structure) {
      setDescriptionFromStruct(model, description);
    }
  }


  /**
   * @param model
   * @param description
   */
  private void setDescriptionFromStruct(Model model, Array description) {
    Struct desc = (Struct) description;
    List<String> descriptionFields = desc.getFieldNames();
    if (descriptionFields.contains(ModelField.name.name())) {
      Array tmp = desc.getObject(ModelField.name.name());
      model.setName(COBRAUtils.asString(tmp));
    }
    if (descriptionFields.contains(ModelField.notes.name())) {
      Array tmp = desc.getObject(ModelField.notes.name());
      if (tmp.getType() == MatlabType.Character) {
        try {
          model.appendNotes(SBMLtools.toNotesString("<p>" + COBRAUtils.asString(tmp) + "</p>"));
        } catch (XMLStreamException exc) {
          COBRAUtils.logException(exc);
        }
      }
    }
  }


  /**
   * @return
   */
  public static MatlabFields getInstance() {
    if (instance == null) {
      throw new IllegalStateException("MatlabFields is not initialized!");
    }
    return instance;
  }


  /**
   * @param name
   * @return
   */
  public Optional<Cell> getCell(String name) {
    if (fields.containsKey(name)) {
      Array array = fields.get(name);
      if (array.getType() == MatlabType.Cell) {
        return Optional.of((Cell) array);
      }
    }
    return Optional.empty();
  }


  /**
   * @param name
   * @return
   */
  public Optional<Char> getChar(String name) {
    if (fields.containsKey(name)) {
      Array array = fields.get(name);
      if (array.getType() == MatlabType.Character) {
        return Optional.of((Char) array);
      }
    }
    return Optional.empty();
  }


  /**
   * @param name
   * @return
   */
  public Optional<Matrix> getMatrix(String name) {
    if (fields.containsKey(name)) {
      Array array = fields.get(name);
      if (array instanceof Matrix) {
        return Optional.of((Matrix) array);
      }
    }
    return Optional.empty();
  }


  /**
   * @param name
   * @return
   */
  public Optional<Sparse> getSparse(String name) {
    if (fields.containsKey(name)) {
      Array array = fields.get(name);
      if (array.getType() == MatlabType.Sparse) {
        return Optional.of((Sparse) array);
      }
    }
    return Optional.empty();
  }
}
