package edu.ucsd.sbrg.bigg.annotation;

import de.zbit.util.ResourceManager;
import edu.ucsd.sbrg.bigg.BiGGId;
import edu.ucsd.sbrg.bigg.Parameters;
import edu.ucsd.sbrg.db.AnnotateDB;
import edu.ucsd.sbrg.db.BiGGDB;
import edu.ucsd.sbrg.db.QueryOnce;
import edu.ucsd.sbrg.miriam.Registry;
import org.sbml.jsbml.CVTerm;
import org.sbml.jsbml.CVTerm.Qualifier;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBase;
import org.sbml.jsbml.Species;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.BIGG_METABOLITE;
import static edu.ucsd.sbrg.db.AnnotateDBContract.Constants.BIGG_REACTION;
import static java.text.MessageFormat.format;

public abstract class CVTermAnnotation {

  /**
   * Localization support.
   */
  private static final transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");

  abstract void annotate();


  abstract Optional<BiGGId> checkId();


  abstract void addAnnotations(BiGGId biggId);


  /**
   * Common annotation method for species and reactions, as they shared much of their code
   *
   * @param node:
   *        {@link Reaction} or {@link Species} to get annotations for
   * @throws IllegalArgumentException
   *         if passed {@link SBase} is not a {@link Species} or {@link Reaction}, as the method is only applicable for
   *         both those cases
   */
  void addAnnotations(SBase node, BiGGId biggId) throws IllegalArgumentException {
    if (!(node instanceof Species) && !(node instanceof Reaction)) {
      throw new IllegalArgumentException(format(MESSAGES.getString("ANNOTATION_WRONG_SBASE"), node.getClass().getName()));
    }
    // Fetch annotations already present on the SBase
    CVTerm cvTerm = null;
    for (CVTerm term : node.getAnnotation().getListOfCVTerms()) {
      if (term.getQualifier() == Qualifier.BQB_IS) {
        cvTerm = term;
        node.removeCVTerm(term);
        break;
      }
    }
    if (cvTerm == null) {
      cvTerm = new CVTerm(Qualifier.BQB_IS);
    }
    Set<String> annotations = new HashSet<>();
    Parameters parameters = Parameters.get();
    boolean isReaction = node instanceof Reaction;
    if (isReaction) {
      boolean isBiGGReaction = QueryOnce.isReaction(biggId.getAbbreviation());
      // using BiGG Database
      if (isBiGGReaction) {
        annotations.add(Registry.createURI("bigg.reaction", biggId));
      }
      Set<String> biggAnnotations = BiGGDB.getResources(biggId, parameters.includeAnyURI(), true);
      annotations.addAll(biggAnnotations);
      // using AnnotateDB
      if (parameters.addADBAnnotations() && AnnotateDB.inUse() && isBiGGReaction) {
        Set<String> adbAnnotations = AnnotateDB.getAnnotations(BIGG_REACTION, biggId.toBiGGId());
        annotations.addAll(adbAnnotations);
      }
    } else {
      boolean isBiGGMetabolite = QueryOnce.isMetabolite(biggId.getAbbreviation());
      // using BiGG Database
      if (isBiGGMetabolite) {
        annotations.add(Registry.createURI("bigg.metabolite", biggId));
      }
      Set<String> biggAnnotations = BiGGDB.getResources(biggId, parameters.includeAnyURI(), false);
      annotations.addAll(biggAnnotations);
      // using AnnotateDB
      if (parameters.addADBAnnotations() && AnnotateDB.inUse() && isBiGGMetabolite) {
        Set<String> adb_annotations = AnnotateDB.getAnnotations(BIGG_METABOLITE, biggId.toBiGGId());
        annotations.addAll(adb_annotations);
      }
    }
    // don't add resources that are already present
    Set<String> existingAnnotations =
      cvTerm.getResources().stream()
            .map(resource -> resource.replaceAll("http://identifiers.org", "https://identifiers.org"))
            .collect(Collectors.toSet());
    annotations.removeAll(existingAnnotations);
    // adding annotations to cvTerm
    List<String> sortedAnnotations = new ArrayList<>(annotations);
    Collections.sort(sortedAnnotations);
    for (String annotation : sortedAnnotations) {
      cvTerm.addResource(annotation);
    }
    if (cvTerm.getResourceCount() > 0) {
      node.addCVTerm(cvTerm);
    }
    if ((node.getCVTermCount() > 0) && !node.isSetMetaId()) {
      node.setMetaId(node.getId());
    }
  }
}
