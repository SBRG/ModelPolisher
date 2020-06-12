package edu.ucsd.sbrg.parsers.json.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.text.MessageFormat.format;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Metabolite {

  private static final Logger logger = Logger.getLogger(Metabolite.class.getName());
  @JsonProperty(required = true)
  private String id;
  @JsonProperty(required = true)
  private String name;
  @JsonProperty(required = true)
  private String compartment;
  private int charge;
  private String formula;
  private double bound;
  private Object notes;
  private Object annotation;

  public Metabolite() {
    // Init default values
    bound = 0;
    // Set defaults for some required properties, as @JsonProperty(required = true) does not seem to work
    name = "";
    compartment = "";
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


  public String getCompartment() {
    return compartment;
  }


  public void setCompartment(String compartment) {
    Pattern validCompartmentCode = Pattern.compile("(C_)?[a-z]{1,2}");
    if (validCompartmentCode.matcher(compartment).find()) {
      if(compartment.startsWith("C_")){
        compartment = compartment.substring(2);
      }
      this.compartment = compartment;
    } else {
      logger.finest(format(
        "Compartment code '{0}' in metabolite '{1}' did not match pattern (C_)?[a-z]'{'1,2'}', trying to extract from id after parsing",
        compartment, id));
    }
  }


  public int getCharge() {
    return charge;
  }


  public void setCharge(int charge) {
    this.charge = charge;
  }


  public String getFormula() {
    return formula;
  }


  public void setFormula(String formula) {
    this.formula = formula;
  }


  public double getBound() {
    return bound;
  }


  public void setBound(double bound) {
    this.bound = bound;
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
