package edu.ucsd.sbrg.bigg.polishing;

import edu.ucsd.sbrg.miriam.Registry;
import org.sbml.jsbml.ext.fbc.GeneProduct;

import edu.ucsd.sbrg.bigg.BiGGId;

public class GeneProductPolishing {

  private final GeneProduct geneProduct;

  public GeneProductPolishing(GeneProduct geneProduct) {
    this.geneProduct = geneProduct;
  }


  /**
   */
  public void polish() {
    Registry.processResources(geneProduct.getAnnotation());
    String label = null;
    if (geneProduct.isSetLabel() && !geneProduct.getLabel().equalsIgnoreCase("None")) {
      label = geneProduct.getLabel();
    } else if (geneProduct.isSetId()) {
      label = geneProduct.getId();
    }
    if (label == null) {
      return;
    }
    BiGGId.createGeneId(geneProduct.getId()).ifPresent(biggId -> {
      String id = biggId.toBiGGId();
      if (!id.equals(geneProduct.getId())) {
        geneProduct.setId(id);
      }
      if (geneProduct.getCVTermCount() > 0) {
        geneProduct.setMetaId(id);
      }
    });
    if (!geneProduct.isSetName() || geneProduct.getName().equalsIgnoreCase("None")) {
      geneProduct.setName(label);
    }
  }
}
