package edu.ucsd.sbrg.parsers.cobra;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * For more information about COBRA model fields, see the following
 * <a href=
 * "http://www.nature.com/protocolexchange/system/uploads/1808/original/Supplementary_Material.pdf?1304792680">Supplementary
 * Material</a>.
 * 
 * @author Andreas Dr&auml;ger
 */
public enum ModelField {

  /**
   * Matrix of constraints, form <i>&mu; &sdot; A &sdot; v + B &sdot; v =
   * 0</i> or g(<i>&mu;</i>) &sdot; <i>A &sdot; v + B &sdot; v = 0</i> with
   * g(<i>&mu;</i>) being some continuous nonlinear function of <i>&mu</i>;
   * 
   * @see #B
   */
  A,
  /**
   * Can be part of the {@link #description}.
   */
  author,
  /**
   * The bound vector of metabolite concentration change rates (usually but not
   * always all zero, i.e., steady-state): <i>S &sdot; v = b</i>. Must have same
   * dimension as {@link #mets}.
   * <p>
   * The mat files where the b vector isn't zero include constraints that couple
   * the flux through the model's reactions to the BOF. These are mat files
   * joining reconstructions of two or more species. These constraints are
   * represented in the b and A fields.
   * <p>
   * If this cannot be expressed in SBML, then an error message would be
   * appropriate. The SBML file would probably not result in a functional joined
   * model though.
   * <p>
   * Data type: double array. Length must be identical to the number of reactions.
   */
  b,
  /**
   * Matrix of constraints, form <i>&mu; &sdot; A &sdot; v + B &sdot; v =
   * 0</i> or g(<i>&mu;</i>) &sdot; <i>A &sdot; v + B &sdot; v = 0</i> with
   * g(<i>&mu;</i>) being some continuous nonlinear function of <i>&mu</i>;
   * 
   * @see #A
   */
  B,
  /**
   * The objective function vector for max(<i>c' &sdot; v</i>) for corresponding
   * reactions. The dimension of this element must be identical to the number of
   * reactions. Dimensions that have a zero value in this field, do not
   * contribute to the objective function.
   * <p>
   * Data type: double array.
   * Corresponds to {@link FluxObjective#getCoefficient()}.
   */
  c,
  /**
   * Literature references for the reactions.
   */
  citations,
  /**
   * TODO
   */
  coefficients,
  /**
   * These are human-readable notes for reactions.
   */
  comments,
  /**
   * Confidence score for each reaction.
   * Confidence scores must have the same dimension as the reactions. These are
   * an optional input, but it provides additional information on the reaction
   * content. Adding them as notes would be a good idea.
   */
  confidenceScores,
  /**
   * The csense field expresses equality constraints in the model. If this field
   * is not defin object.getType();ed in the reconstruction, equality constraints are assumed when
   * performing the optimization. Its value is a {@link String} whose length
   * must equal the number of metabolites. An 'E' at index <i>i</i> means that
   * in this dimension <i>S &sdot; v = b</i> (equality), 'G' means &ge; greater
   * than or equal to and an 'L' denotes &le;.
   * 
   * @see #osense
   */
  csense,
  /**
   * Human-redable information about the model, e.g., the model name. This field
   * can optionally have sub-entries.
   */
  description,
  /**
   * The "disabled" field comes from the reconstruction tool rBioNet.
   * The tool offers the option to disable certain reactions.
   * If no reactions are disabled, then the "disabled" field in empty.
   */
  disabled,
  /**
   * The Enzyme Commission codes for the reactions. Length must be identical to
   * the number of reactions.
   */
  ecNumbers,
  /**
   * Can be part of the {@link #description}.
   */
  genedate,
  /**
   * Can be part of the {@link #description}.
   */
  geneindex,
  /**
   * The list of all genes in the model, where each contained gene corresponds
   * to a {@link GeneProduct#getId()}. Data type: cell array of string.
   */
  genes,
  /**
   * Can be part of the {@link #description}.
   */
  genesource,
  /**
   * Boolean gene-protein-reaction (GPR) rules in a readable format (AND/OR).
   * Data type: cell array of strings.
   * Example: {@code (8639.1) or (26.1) or (314.2) or (314.1)}. Dimensions must
   * be identical to the number of reactions. Corresponds to
   * {@link GeneProductAssociation}
   */
  grRules,
  /**
   * Lower reaction flux bounds for corresponding reactions
   */
  lb,
  /**
   * Value of charge for corresponding metabolite.
   * Must have same dimension as {@link #mets}. Data type: double array.
   * For SBML Level < 3, it corresponds to {@link Species#getCharge()}. Since
   * Level 3, it corresponds to {@link FBCSpeciesPlugin#getCharge()}.
   */
  metCharge,
  /**
   * Optional: if present, it must have same dimension as {@link #mets}.
   * Data type: cell array of strings. Corresponds to the annotation of
   * {@link Species}
   */
  metCHEBIID,
  /**
   * Elemental formula for each metabolite. This must have same dimension as
   * {@link #mets}. Datatype: cell array of strings. Corresponds to
   * {@link FBCSpeciesPlugin#getChemicalFormula()}.import org.sbml.jsbml.ext.fbc.FBCSpeciesPlugin;
   * import org.sbml.jsbml.ext.fbc.FluxObjective;
   * import org.sbml.jsbml.ext.fbc.GeneProduct;
   * import org.sbml.jsbml.ext.fbc.GeneProductAssociation;
   */
  metFormulas,
  /**
   * Optional: if present, it must have same dimension as {@link #mets}.
   * Data type: cell array of strings.
   */
  metHMDB,
  /**
   * Inichi String for each corresponding metabolite.
   * Optional: if present, it must have same dimension as {@link #mets}.
   * Data type: cell array of strings.
   */
  metInchiString,
  /**
   * KEGG ID for each corresponding metabolite.
   * Optional: if present, it must have same dimension as {@link #mets}.
   * Data type: cell array of strings.
   */
  metKeggID,
  /**
   * Descriptive metabolite names, must have same dimension as {@link #mets}.
   * Datatype: cell array of strings. Corresponds to {@link Species#getName()}
   */
  metNames,
  /**
   * Pub Chem ID for each corresponding metabolite.
   * Optional: if present, it must have same dimension as {@link #mets}.
   * Data type: cell array of strings.
   */
  metPubChemID,
  /**
   * Metabolite name abbreviation; metabolite ID; order corresponds to S matrix.
   * Metabolite BiGG ids (incl. compartment code). Data type: cell array of
   * strings. Corresponds to {@link Species#getId()}.
   */
  mets,
  /**
   * Optional: if present, it must have same dimension as {@link #mets}.
   */
  metSmile,
  /**
   * Can be part of the {@link #description}.
   */
  name,
  /**
   * Can be part of the {@link #description}.
   */
  notes,
  /**
   * Can be part of the {@link #description}.
   */
  organism,
  /**
   * Objective sense, i.e., to minimize or maximize the objective. This field
   * can either be defined in the mat file itself or when performing an
   * optimization function (e.g., optimizeCbModel).
   */
  osense,
  /**
   * Proteins associated with each reaction.
   */
  proteins,
  /**
   * A vector consisting of zeros and ones that translate to binary and determine
   * if the corresponding reaction of that index is reversible (1) or not (0).
   * Dimensions must be equal to the number of reactions. Corresponds to the
   * value {@link Reaction#isReversible()}
   */
  rev,
  /**
   * Boolean rule for the corresponding reaction which defines gene-reaction
   * relationship.
   */
  rules,
  /**
   * TODO: description COG
   */
  rxnCOG,
  /**
   * Cell array of strings, can any value in a range of 0 to 4. Length must be
   * equal to the number of reactions.
   */
  rxnConfidenceEcoIDA,
  /**
   * @see #confidenceScores.
   */
  rxnConfidenceScores,
  /**
   * The Systems Biology Ontology Term to describe the role of a reaction.
   * Length must be identical to the number of reactions. Value corresponds to
   * the attribute {@link Reaction#getSBOTerm()}
   */
  rxnsboTerm,
  /**
   * A matrix with rows corresponding to reaction list and columns corresponding
   * to gene list. Data type: sparse double. Size of this matrix must be number
   * of reactions times number of genes. No counterpart in FBC v2.
   */
  rxnGeneMat,
  /**
   * Reaction identifiers in KEGG, but sometimes also contains {@link #ecNumbers}.
   * Length must be identical to the number of reactions. Entries belong to the
   * annotation of {@link Reaction}.
   */
  rxnKeggID,
  /**
   * Descriptive reaction names, length of this array must be identical to the
   * number of reactions. Data type: cell array of string. Corresponds to the
   * name of a {@link Reaction}.
   */
  rxnNames,
  /**
   * TODO: description KEGG Orthology
   */
  rxnKeggOrthology,
  /**
   * E. C. number for each reaction
   * 
   * @see #ecNumbers
   */
  rxnECNumbers,
  /**
   * Cell array of strings which can contain optional information on references
   * for each specific reaction.
   * Example:
   * 
   * <pre>
   * 'Na coupled transport of pyruvate, lactate, and short chain fatty acids, i.e., acetate, propionate, and butyrate mediated by SMCT1
   * </pre>
   */
  rxnReferences,
  /**
   * Reaction BiGG ids. Corresponds to the id of {@link Reaction}.
   * Reaction name abbreviation; reaction ID; order corresponds to S matrix.
   */
  rxns,
  /**
   * Cell array of strings. Text notes (description) about reactions. Length
   * must be identical to the number of reactions.
   */
  rxnNotes,
  /**
   * Stoichiometric matrix in sparse format.
   */
  S,
  /**
   * This defines groups of reactions that belong to a common reaction subsystem.
   * Their number must hence be identical to the reaction count. Subsystems are
   * listed repeatedly in the source file.
   * If present, the size of this field must be identical to the number of
   * reactions.
   * Data type: cell array of strings.
   */
  subSystems,
  /**
   * Upper reaction flux bounds for corresponding reactions
   */
  ub;

  /**
   * Get known model field variant name for a struct field, disregarding upper/lowercase discrepancies, if case can't be
   * matched
   *
   * @param query:
   *        Possible model field, present in model struct
   * @return List of matching ModelField variant names
   */
  public static List<String> getCorrectName(String query) {
    List<String> normalVariant = Arrays.stream(ModelField.values()).map(Enum::name).filter(name -> name.equals(query))
                                       .collect(Collectors.toList());
    if (normalVariant.size() != 1) {
      return Arrays.stream(ModelField.values()).map(Enum::name)
                   .filter(name -> name.toLowerCase().equals(query.toLowerCase())).collect(Collectors.toList());
    } else {
      return normalVariant;
    }
  }


  /**
   * Get known model field variant name for a struct field, disregarding upper/lowercase discrepancies using struct
   * field as prefix of knwon model field
   *
   * @param query
   * @return
   */
  public static List<String> getNameForPrefix(String query) {
    return Arrays.stream(ModelField.values()).map(Enum::name)
                 .filter(name -> name.toLowerCase().startsWith(query.toLowerCase())).collect(Collectors.toList());
  }
}
