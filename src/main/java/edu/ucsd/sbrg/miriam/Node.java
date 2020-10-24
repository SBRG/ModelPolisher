package edu.ucsd.sbrg.miriam;

import java.util.Optional;

public interface Node {

  Optional<String> extractId(String query);

  String getName();

  String getURLWithPattern();


  String resolveID(String id);


  boolean isMatch(String query);


  boolean isDeprecated();
}
