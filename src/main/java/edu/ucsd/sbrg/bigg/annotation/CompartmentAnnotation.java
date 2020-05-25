package edu.ucsd.sbrg.bigg.annotation;

import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.SBO;

import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.db.BiGGDB;
import edu.ucsd.sbrg.db.QueryOnce;
import edu.ucsd.sbrg.miriam.Registry;

public class CompartmentAnnotation {
  /**
   * Instance of compartment to annotate
   */
  private final Compartment compartment;

  public CompartmentAnnotation(Compartment compartment) {
    this.compartment = compartment;
  }


  /**
   * Adds bigg and SBO annotation for the given compartment and sets its name from BiGG, if no name is set or if it is
   * the default compartment.
   * Only works for compartment codes contained in BiGG Knowledgebase
   */
  public void annotate() {
    BiGGId biggId = new BiGGId(compartment.getId());
    if (QueryOnce.isCompartment(biggId.getAbbreviation())) {
      compartment.addCVTerm(new CVTerm(CVTerm.Qualifier.BQB_IS, Registry.createURI("bigg.compartment", biggId)));
      compartment.setSBOTerm(SBO.getCompartment()); // physical compartment
      if (!compartment.isSetName() || compartment.getName().equals("default")) {
        BiGGDB.getCompartmentName(biggId).ifPresent(compartment::setName);
      }
    }
  }
}
