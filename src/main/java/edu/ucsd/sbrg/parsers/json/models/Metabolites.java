package edu.ucsd.sbrg.parsers.json.models;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Metabolites {

  private Map<String, Double> metabolites = new HashMap<>();

  @JsonAnySetter
  public void add(String key, double value) {
    metabolites.put(key, value);
  }


  @JsonGetter
  public Map<String, Double> get() {
    return metabolites;
  }


  @JsonSetter
  public void set(Map<String, Double> metabolites) {
    this.metabolites = metabolites;
  }
}
