package edu.ucsd.sbrg.bigg.polishing;

import edu.ucsd.sbrg.bigg.BiGGId;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Unit;

import java.util.logging.Logger;

import static java.text.MessageFormat.format;

public class CompartmentPolishing {

  private final static transient Logger logger = Logger.getLogger(CompartmentPolishing.class.getName());
  private final Compartment compartment;

  public CompartmentPolishing(Compartment compartment) {
    this.compartment = compartment;
  }


  /**
   *
   */
  public void polish() {
    if (!compartment.isSetId()) {
      compartment.setId("d"); // default
    } else {
      // remove C_ prefix of compartment code, not in BiGGId specification
      BiGGId.extractCompartmentCode(compartment.getId()).ifPresentOrElse(compartment::setId,
        () -> logger.warning(format("CompartmentCode '{0}' is not BiGGId conform.", compartment.getId())));
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
