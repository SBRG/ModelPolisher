package edu.ucsd.sbrg.parsers.json.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"id", "name", "metabolites", "gene_reaction_rule", "lower_bound", "upper_boound",
  "objective_coefficient", "subsystem", "notes", "annotation"})
public class Reaction {

  @JsonProperty(required = true)
  private String id;
  @JsonProperty(required = true)
  private String name;
  @JsonProperty(required = true)
  private Metabolites metabolites;
  @JsonProperty(value = "gene_reaction_rule", required = true)
  private String geneReactionRule;
  @JsonProperty(value = "lower_bound", required = true)
  private double lowerBound;
  @JsonProperty(value = "upper_bound", required = true)
  private double upperBound;
  @JsonProperty(value = "objective_coefficient")
  private double objectiveCoefficient;
  private String subsystem;
  private Object notes;
  private Object annotation;

  public Reaction() {
    // Init default
    objectiveCoefficient = 0;
    // Set defaults for some required properties, as @JsonProperty(required = true) does not seem to work
    name = "";
    geneReactionRule = "";
    lowerBound = 0;
    upperBound = 0;
  }


  public String getId() {
    return id;
  }


  public void setId(String id) {
    this.id = id;
  }


  public String getName() {
    return name;
  }


  public void setName(String name) {
    this.name = name;
  }


  public Metabolites getMetabolites() {
    return metabolites;
  }


  public void setMetabolites(Metabolites metabolites) {
    this.metabolites = metabolites;
  }


  public String getGeneReactionRule() {
    return geneReactionRule;
  }


  public void setGeneReactionRule(String geneReactionRule) {
    this.geneReactionRule = geneReactionRule;
  }


  public double getLowerBound() {
    return lowerBound;
  }


  public void setLowerBound(double lowerBound) {
    this.lowerBound = lowerBound;
  }


  public double getUpperBound() {
    return upperBound;
  }


  public void setUpperBound(double upperBound) {
    this.upperBound = upperBound;
  }


  public double getObjectiveCoefficient() {
    return objectiveCoefficient;
  }


  public void setObjectiveCoefficient(double objectiveCoefficient) {
    this.objectiveCoefficient = objectiveCoefficient;
  }


  public String getSubsystem() {
    return subsystem;
  }


  public void setSubsystem(String subsystem) {
    this.subsystem = subsystem;
  }


  public Object getNotes() {
    return notes;
  }


  public void setNotes(Object notes) {
    this.notes = notes;
  }


  public Object getAnnotation() {
    return annotation;
  }


  public void setAnnotation(Object annotation) {
    this.annotation = annotation;
  }
}
