package edu.ucsd.sbrg.util;

import de.zbit.util.ResourceManager;
import org.sbml.jsbml.Compartment;
import org.sbml.jsbml.Reaction;
import org.sbml.jsbml.SBMLDocument;
import org.sbml.jsbml.Species;
import org.sbml.jsbml.ext.fbc.FBCConstants;
import org.sbml.jsbml.ext.fbc.FBCModelPlugin;
import org.sbml.jsbml.ext.fbc.GeneProduct;
import org.sbml.jsbml.xml.XMLNode;
import org.sbml.jsbml.xml.parsers.SBMLRDFAnnotationParser;
import org.w3c.tidy.Tidy;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

public class CombineArchive {

  /**
   * A {@link Logger} for this class.
   */
  private static final transient Logger logger = Logger.getLogger(CombineArchive.class.getName());
  /**
   * Bundle for ModelPolisher logger messages
   */
  public static transient ResourceBundle MESSAGES = ResourceManager.getBundle("edu.ucsd.sbrg.polisher.Messages");
  /**
   * {@link SBMLDocument} to write glossary for
   */
  private final SBMLDocument doc;
  /**
   * Output location for the combine archive
   */
  private final File output;

  public CombineArchive(SBMLDocument doc, File output) {
    this.doc = doc;
    this.output = output;
  }


  public void write() throws IOException, XMLStreamException {
    writeGlossary();
    writeCombineArchive();
  }


  /**
   * @throws XMLStreamException:
   *         propagated from {@link #getGlossary(SBMLDocument)} and #TidySBMLWriter.write
   * @throws IOException:
   *         propagated from {@link #writeTidyRDF(File, String)}
   */
  private void writeGlossary() throws XMLStreamException, IOException {
    String glossary = getGlossary(doc);
    String glossaryLocation =
      output.getAbsolutePath().substring(0, output.getAbsolutePath().lastIndexOf('.')) + "_glossary.rdf";
    logger.info(format(MESSAGES.getString("WRITE_RDF_FILE_INFO"), glossaryLocation));
    writeTidyRDF(new File(glossaryLocation), glossary);
  }


  /**
   */
  private void writeCombineArchive() {
    try {
      String baseLocation = output.getAbsolutePath().substring(0, output.getAbsolutePath().lastIndexOf('.'));
      String glossaryLocation = baseLocation + "_glossary.rdf";
      String combineArcLocation = baseLocation + ".zip";
      // check if archive file exists and delete
      File caFile = new File(combineArcLocation);
      if (caFile.exists()) {
        if (!caFile.delete()) {
          logger.severe(format("Failed to delete archive file \"{0}\"", caFile.getPath()));
        }
      }
      // build and pack archive
      de.unirostock.sems.cbarchive.CombineArchive ca = new de.unirostock.sems.cbarchive.CombineArchive(caFile);
      File outputXML = new File(output.getAbsolutePath());
      File outputRDF = new File(glossaryLocation);
      ca.addEntry(outputXML, "model.xml", new URI("http://identifiers.org/combine.specifications/sbml"), true);
      ca.addEntry(outputRDF, "glossary.rdf",
        // generated from https://sems.uni-rostock.de/trac/combine-ext/wiki/CombineFormatizer
        new URI("http://purl.org/NET/mediatypes/application/rdf+xml"), true);
      logger.info(format(MESSAGES.getString("WRITE_RDF_FILE_INFO"), combineArcLocation));
      ca.pack();
      ca.close();
      // clean up original of packed files
      boolean rdfDeleted = outputRDF.delete();
      boolean outputXMLDeleted = outputXML.delete();
      logger.info(format(MESSAGES.getString("DELETE_FILE"), outputXML.getParent(), outputXMLDeleted));
      logger.info(format(MESSAGES.getString("DELETE_FILE"), outputRDF.getParent(), rdfDeleted));
    } catch (Exception e) {
      logger.warning("Exception while producing COMBINE Archive:");
      e.printStackTrace();
    }
  }


  /**
   * @param doc:
   *        SBMLDocument to produce glossary for
   * @return Glossary as XMLString or empty string, if either model is null or has no children
   */
  private String getGlossary(SBMLDocument doc) throws XMLStreamException {
    SBMLRDFAnnotationParser rdfParser = new SBMLRDFAnnotationParser();
    if (rdfParser.writeAnnotation(doc.getModel(), null) != null
      && rdfParser.writeAnnotation(doc.getModel(), null).getChild(1) != null) {
      XMLNode node = rdfParser.writeAnnotation(doc.getModel(), null).getChild(1);
      for (Species s : doc.getModel().getListOfSpecies()) {
        XMLNode tempNode = rdfParser.writeAnnotation(s, null);
        if (tempNode != null && tempNode.getChildCount() != 0) {
          node.addChild(tempNode.getChild(1));
        }
      }
      for (Reaction r : doc.getModel().getListOfReactions()) {
        XMLNode tempNode = rdfParser.writeAnnotation(r, null);
        if (tempNode != null && tempNode.getChildCount() != 0) {
          node.addChild(tempNode.getChild(1));
        }
      }
      for (Compartment c : doc.getModel().getListOfCompartments()) {
        XMLNode tempNode = rdfParser.writeAnnotation(c, null);
        if (tempNode != null && tempNode.getChildCount() != 0) {
          node.addChild(tempNode.getChild(1));
        }
      }
      if (doc.getModel().isSetPlugin(FBCConstants.shortLabel)) {
        FBCModelPlugin fbcModelPlugin = (FBCModelPlugin) doc.getModel().getPlugin(FBCConstants.shortLabel);
        for (GeneProduct gP : fbcModelPlugin.getListOfGeneProducts()) {
          XMLNode tempNode = rdfParser.writeAnnotation(gP, null);
          if (tempNode != null && tempNode.getChildCount() != 0) {
            node.addChild(tempNode.getChild(1));
          }
        }
      }
      return node.toXMLString();
    } else {
      return "";
    }
  }


  /**
   * @param outputFile,
   *        rdfString
   */
  private void writeTidyRDF(File outputFile, String rdfString) throws FileNotFoundException {
    Tidy tidy = new Tidy(); // obtain a new Tidy instance
    tidy.setDropEmptyParas(false);
    tidy.setHideComments(false);
    tidy.setIndentContent(true);
    tidy.setInputEncoding("UTF-8");
    tidy.setOutputEncoding("UTF-8");
    tidy.setQuiet(true);
    tidy.setSmartIndent(true);
    tidy.setTrimEmptyElements(true);
    tidy.setWraplen(0);
    tidy.setWrapAttVals(false);
    tidy.setWrapScriptlets(true);
    tidy.setLiteralAttribs(true);
    tidy.setXmlOut(true);
    tidy.setXmlSpace(true);
    tidy.setXmlTags(true);
    tidy.setSpaces(2);
    Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8));
    InputStreamReader in = new InputStreamReader(new ByteArrayInputStream(rdfString.getBytes(StandardCharsets.UTF_8)),
      StandardCharsets.UTF_8);
    tidy.parse(in, out);
    try {
      in.close();
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
