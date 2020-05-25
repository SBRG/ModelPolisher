package edu.ucsd.sbrg.bigg.annotation;

import edu.ucsd.sbrg.bigg.BiGGId;

import java.util.Optional;

public interface CVTermAnnotation {

  void annotate();


  Optional<BiGGId> checkId();


  void setCVTermResources(BiGGId biggId);
}
