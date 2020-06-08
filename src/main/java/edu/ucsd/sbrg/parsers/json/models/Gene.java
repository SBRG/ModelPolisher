package edu.ucsd.sbrg.parsers.json.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Gene {

  @JsonProperty(required = true)
  private String id;
  @JsonProperty(required = true)
  private String name;
  private Object notes;
  private Object annotation;

  public Gene() {
    // Set defaults for some required properties, as @JsonProperty(required = true) does not seem to work
    name = "";
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
