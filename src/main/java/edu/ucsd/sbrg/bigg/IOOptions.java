/*
 * 
 */
package edu.ucsd.sbrg.bigg;

import de.zbit.io.filefilter.MultipleFileFilter;
import de.zbit.io.filefilter.SBFileFilter;
import de.zbit.util.prefs.KeyProvider;
import de.zbit.util.prefs.Option;
import de.zbit.util.prefs.Range;

import java.io.File;

import static edu.ucsd.sbrg.bigg.ModelPolisher.MESSAGES;

/**
 * @author Andreas Dr&auml;ger
 */
public interface IOOptions extends KeyProvider {

  /**
   * Specifies the SBML or MAT input file. If a directory is given, the
   * conversion
   * will be recursively performed. MAT files will be converted to SBML prior to
   * polishing.
   */
  public static final Option<File> INPUT  = new Option<File>("INPUT",
      File.class, "Input SBML file",
      new Range<File>(File.class,
          new MultipleFileFilter(MESSAGES.getString("INPUT_DESC"),
            SBFileFilter.createSBMLFileFilter(), SBFileFilter.createMATFileFilter(),
            SBFileFilter.createJSONFileFilter(),
            SBFileFilter.createDirectoryFilter())));
  /**
   * The path to the file into which the output should be written. If the
   * input is a directory, this must also be a directory in order to perform a
   * recursive conversion.
   */
  public static final Option<File> OUTPUT =
      new Option<File>("OUTPUT", File.class, MESSAGES.getString("OUTPUT_DESC"),
          new Range<File>(File.class,
              new MultipleFileFilter("SBML", SBFileFilter.createSBMLFileFilter(),
                SBFileFilter.createDirectoryFilter())));
}
