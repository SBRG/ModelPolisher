package edu.ucsd.sbrg.bigg.polishing;

import static java.text.MessageFormat.format;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import edu.ucsd.sbrg.miriam.Registry;
import org.sbml.jsbml.Annotation;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Unit;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;

public class CompartmentPolishing {

  /**
   * A {@link Logger} for this class.
   */
  private final static transient Logger logger = Logger.getLogger(CompartmentPolishing.class.getName());
  /**
   * Localization support.
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  private final Compartment compartment;

  public CompartmentPolishing(Compartment compartment) {
    this.compartment = compartment;
  }


  /**
   *
   */
  public void polish() {
    Registry.processResources(compartment.getAnnotation());
    if (!compartment.isSetId()) {
      compartment.setId("d"); // default
    } else {
      // remove C_ prefix of compartment code, not in BiGGId specification
      BiGGId.extractCompartmentCode(compartment.getId()).ifPresentOrElse(compartment::setId,
        () -> logger.warning(format(MESSAGES.getString("COMPARTMENT_CODE_WRONG_FORMAT"), compartment.getId())));
    }
    compartment.setSBOTerm(410); // implicit compartment
    if (!compartment.isSetName()) {
      // TODO: make the name of a compartment a user setting
      compartment.setName("default");
    }
    if (!compartment.isSetMetaId() && (compartment.getCVTermCount() > 0)) {
      compartment.setMetaId(compartment.getId());
    }
    if (!compartment.isSetConstant()) {
      compartment.setConstant(true);
    }
    if (!compartment.isSetSpatialDimensions()) {
      // TODO: check with biGG id, not for surfaces etc.
      // c.setSpatialDimensions(3d);
    }
    if (!compartment.isSetUnits()) {
      Model model = compartment.getModel();
      // Let's take the model's default unless we don't have anything defined.
      if ((model == null) || !(model.isSetLengthUnits() || model.isSetAreaUnits() || model.isSetVolumeUnits())) {
        // TODO: set compartment units.
        /*
         * This is a temporary solution until we agree on something better.
         */
        compartment.setUnits(Unit.Kind.DIMENSIONLESS);
      }
    }
  }
}
