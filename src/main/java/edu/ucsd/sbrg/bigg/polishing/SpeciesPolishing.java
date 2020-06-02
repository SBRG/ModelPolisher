package edu.ucsd.sbrg.bigg.polishing;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.NamedSBase;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

public class SpeciesPolishing {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(SpeciesPolishing.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  public static transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   *
   */
  private final Species species;

  public SpeciesPolishing(Species species) {
    this.species = species;
  }


  /**
   * @return
   */
  public Optional<Species> polish() {
    String id = species.getId();
    if (id.isEmpty()) {
      // remove species with missing id, produces invalid SBML
      if (species.isSetName()) {
        logger.severe(format(
          "Removing species '{0}' due to missing id. Check your Model for entries missing the id attribute or duplicates.",
          species.getName()));
      } else {
        logger.severe("Removing species with missing id and name. Check your Model for species without id and name.");
      }
      return Optional.of(species);
    }
    if (species.getId().endsWith("_boundary")) {
      logger.warning(format(MESSAGES.getString("SPECIES_ID_INVALID"), id));
      id = id.substring(0, id.length() - 9);
      boolean uniqueId = species.getModel().findUniqueNamedSBase(id) == null;
      if (uniqueId) {
        if (!species.isSetBoundaryCondition() || !species.isBoundaryCondition()) {
          logger.warning(format(MESSAGES.getString("BOUNDARY_FLAG_MISSING"), id));
          species.setBoundaryCondition(true);
        }
      }
    } else if (!species.isSetBoundaryCondition()) {
      species.setBoundaryCondition(false);
    }
    /*
     * Set mandatory attributes to default values
     * TODO: make those maybe user settings.
     */
    if (!species.isSetHasOnlySubstanceUnits()) {
      species.setHasOnlySubstanceUnits(true);
    }
    if (!species.isSetConstant()) {
      species.setConstant(false);
    }
    if ((species.getCVTermCount() > 0) && !species.isSetMetaId()) {
      species.setMetaId(species.getId());
    }
    BiGGId.createMetaboliteId(id).ifPresent(biggId -> {
      if (biggId.isSetCompartmentCode() && species.isSetCompartment()
        && !biggId.getCompartmentCode().equals(species.getCompartment())) {
        logger.warning(format(MESSAGES.getString("CHANGE_COMPART_REFERENCE"), species.getId(), species.getCompartment(),
          biggId.getCompartmentCode()));
        species.setCompartment(biggId.getCompartmentCode());
      }
    });
    checkCompartment(species);
    return Optional.empty();
  }


  /**
   * @param nsb
   */
  public void checkCompartment(NamedSBase nsb) {
    if ((nsb instanceof Species) && !((Species) nsb).isSetCompartment()) {
      Optional<BiGGId> biggId = BiGGId.createMetaboliteId(nsb.getId());
      boolean setCompartment = false;
      if (biggId.isPresent()) {
        if (biggId.get().isSetCompartmentCode()) {
          ((Species) nsb).setCompartment(biggId.get().getCompartmentCode());
          setCompartment = true;
        }
      }
      if (!setCompartment) {
        return;
      }
    } else if ((nsb instanceof Reaction) && !((Reaction) nsb).isSetCompartment()) {
      return;
    }
    if (nsb instanceof Species) {
      String cId = ((Species) nsb).getCompartment();
      Model model = nsb.getModel();
      SBase candidate = model.findUniqueNamedSBase(cId);
      if (candidate instanceof Compartment) {
        // compartment can't be null here, instanceof would evaluate to false
        CompartmentPolishing compartmentPolishing = new CompartmentPolishing((Compartment) candidate);
        compartmentPolishing.polish();
      } else if (candidate == null) {
        logger.warning(format(MESSAGES.getString("CREATE_MISSING_COMP"), cId, nsb.getId(), nsb.getElementName()));
        CompartmentPolishing compartmentPolishing = new CompartmentPolishing(model.createCompartment(cId));
        compartmentPolishing.polish();
      }
    }
  }
}
