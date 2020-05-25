package edu.ucsd.sbrg.bigg.annotation;

import edu.ucsd.sbrg.bigg.Parameters;
import edu.ucsd.sbrg.db.BiGGDB;
import edu.ucsd.sbrg.db.QueryOnce;
import edu.ucsd.sbrg.miriam.Registry;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Model;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.ext.fbc.GeneProduct;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModelAnnotation {

  private final Model model;

  public ModelAnnotation(Model model) {
    this.model = model;
  }


  /**
   * Process annotations pertaining to the actual {@link Model} and delegate annotation for all {@link Compartment},
   * {@link Species}, {@link Reaction} and {@link GeneProduct} instances in the model
   */
  public void annotate() {
    String id = model.getId();
    BiGGDB.getTaxonId(id).ifPresent(
      taxonId -> model.addCVTerm(new CVTerm(CVTerm.Qualifier.BQB_HAS_TAXON, Registry.createURI("taxonomy", taxonId))));
    if (QueryOnce.isModel(id)) {
      addBiGGModelAnnotations();
    }
    if (!model.isSetMetaId() && (model.getCVTermCount() > 0)) {
      model.setMetaId(model.getId());
    }
  }


  /**
   * Add annotation of genomic sequence/assembly to models contained in BiGG.
   * Only MIRIAM annotations are added, if {@link Parameters#includeAnyURI()} returns {@code false}
   */
  private void addBiGGModelAnnotations() {
    model.addCVTerm(new CVTerm(CVTerm.Qualifier.BQM_IS, Registry.createURI("bigg.model", model.getId())));
    String accession = BiGGDB.getGenomeAccesion(model.getId());
    Matcher refseqMatcher =
      Pattern.compile("^(((AC|AP|NC|NG|NM|NP|NR|NT|NW|XM|XP|XR|YP|ZP)_\\d+)|(NZ_[A-Z]{2,4}\\d+))(\\.\\d+)?$")
             .matcher(accession);
    CVTerm term = new CVTerm(CVTerm.Qualifier.BQB_IS_VERSION_OF);
    if (refseqMatcher.matches()) {
      term.addResource(Registry.createShortURI("refseq:" + accession));
    } else {
      if (Parameters.get().includeAnyURI()) {
        Matcher genomeAssemblyMatcher = Pattern.compile("^GC[AF]_[0-9]{9}\\.[0-9]+$").matcher(accession);
        if (genomeAssemblyMatcher.matches()) {
          // resolution issues with https://identifiers.org/insdc.gca, resolve non MIRIAM way (see Issue #96)
          term.addResource("https://www.ncbi.nlm.nih.gov/assembly/" + accession);
        } else {
          term.addResource("https://www.ncbi.nlm.nih.gov/nuccore/" + accession);
        }
      }
    }
    if (term.getResourceCount() > 0) {
      model.addCVTerm(term);
    }
  }
}
