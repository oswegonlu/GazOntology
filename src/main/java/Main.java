import org.apache.commons.cli.*;
import org.semanticweb.owlapi.model.*;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;

import static org.semanticweb.owlapi.util.OWLAPIStreamUtils.asUnorderedSet;

public class Main {

    private static Set<String> getSynonyms(OWLOntology o, OWLClass owlClass, boolean includeRelated, boolean includeBroad){
        Set<String> syns = new HashSet<>();

        o.annotationAssertionAxioms(owlClass.getIRI())
                .filter(a -> {
                    String propertyname = a.getProperty().getIRI().toString().toLowerCase();
                    return propertyname.contains("exactsynonym") || propertyname.contains("exact_synonym") ||
                            (includeRelated && (propertyname.contains("relatedsynonym") ||
                                    propertyname.contains("related_synonym"))) ||
                            (includeBroad && (propertyname.contains("broadsynonym") ||
                                    propertyname.contains("broad_synonym")));
                })
                .forEach(a -> {
                    try {
                        syns.add(a.annotationValue().asLiteral().orElseThrow(() -> new Exception("No literal value found")).getLiteral());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        return syns;
    }

    private static String getName(OWLOntology o, OWLClass owlClass){
        Optional<OWLAnnotationAssertionAxiom> label = o.annotationAssertionAxioms(owlClass.getIRI())
                .filter(a -> a.getProperty().getIRI().toString().toLowerCase().contains("label"))
                .findFirst();

        if (label.isPresent())
            return label.get().annotationValue().asLiteral().get().getLiteral();
        return "";
    }

    private static Set<OWLClass> relevantClasses(Stream<OWLClass> classes, OWLOntology ont, String ontologyid){
        return classes
                .filter(c -> Util.isBaseOntologyClass(c, ontologyid))
                .filter(c -> !Util.isDeprecatedClass(c, ont)).collect(Collectors.toSet());
    }

    private static Map<IRI, Set<String>> buildIriSynsMap_internal(OWLOntology ont, Set<OWLClass> consideredClasses,
                                                                  boolean includeRelatedSyns, boolean includeBroadSyns){
        Map<IRI, Set<String>> iriSynMap = new HashMap<>();

        for (OWLClass c : consideredClasses){
            Set<String> exactSyns = getSynonyms(ont, c, includeRelatedSyns, includeBroadSyns);
            exactSyns.add(getName(ont, c));
            //exactSyns.add(c.getIRI().getShortForm());
            iriSynMap.put(c.getIRI(), exactSyns);
        }

        return iriSynMap;
    }

    private static Map<IRI, Set<String>> buildIriSynsMap(OWLOntology ont, String ontologyid,
                                                         boolean includeRelatedSyns, boolean includeBroadSyns){
        Set<OWLClass> consideredClasses = relevantClasses(ont.classesInSignature(), ont, ontologyid);

        return buildIriSynsMap_internal(ont, consideredClasses, includeRelatedSyns, includeBroadSyns);
    }

    private static Map<IRI, Set<String>> buildIriSynsMap(OWLOntology ont, String ontologyid, Set<OWLClass> classes,
                                                         boolean includeRelatedSyns, boolean includeBroadSyns){
        Set<OWLClass> consideredClasses = relevantClasses(classes.stream(), ont, ontologyid);

        return buildIriSynsMap_internal(ont, consideredClasses, includeRelatedSyns, includeBroadSyns);
    }

    public static Set<OWLClass> getSubClasses(OWLClassExpression classExpression, boolean direct, OWLReasoner reasoner) {
        NodeSet<OWLClass> subClasses = reasoner.getSubClasses(classExpression, direct);
        return asUnorderedSet(subClasses.entities());
    }

    public static OWLClassExpression getClassExpression(DLQueryParser parser, String classExpressionString){
        return parser.parseClassExpression(classExpressionString.trim());
    }

    private static String generateOutput(Map<IRI, Set<String>> iriSetMap){
        String result = "";
        for (Map.Entry<IRI, Set<String>> e : iriSetMap.entrySet()) {
            for (String s : e.getValue()){
                result += s + ":IRI=" + e.getKey().getShortForm() + System.lineSeparator();
            }
        }
        return result;
    }

    private static void writeSynonymFile(Map<IRI, Set<String>> iriSetMap, String outfname) throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter writer = new PrintWriter(outfname, "UTF-8");
        writer.print(generateOutput(iriSetMap));
        writer.close();
    }

    public static void main(String[] args){
        /***** Command Line Arguments ******/
        Options options = new Options();
        options.addOption("f", "ontologyFile", true, "ontology file to process");
        options.addOption("id", "ontologyID", true, "ontology identifier (e.g., iao)");
        options.addOption("top", "topTerm", true, "top term to include");
        options.addOption("r", "include related (inexact) synonyms");
        options.addOption("b", "include broad (inexact) synonyms");
        options.addOption("d", "direct subclasses only");
        options.addOption("o", "output", true, "output file");

        CommandLineParser cmdParser = new DefaultParser();
        CommandLine cmd = null;

        try {
            cmd = cmdParser.parse( options, args);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        String inputFileName = cmd.getOptionValue("f");
        String ontologyID = cmd.getOptionValue("id");
        String topTerm = cmd.getOptionValue("top");
        String output = cmd.getOptionValue("o");
        boolean directSubclasses = cmd.hasOption("d");
        boolean includeRelated = cmd.hasOption("r");
        boolean includeBroad = cmd.hasOption("b");

        /***** Prepare to read the file... ******/
        if (inputFileName == null){
            System.out.println("You must enter an ontology file name.");
            System.exit(1);
        }

        File ontFile = new File(inputFileName);

        if (ontologyID == null) {
            // Get just the filename without extension.
            ontologyID = inputFileName.substring(inputFileName.lastIndexOf("/")+1).replaceFirst("[.][^.]+$", "");
        }

        System.out.println("The ontology ID prefix is: " + ontologyID);

        /***** Read the ontology... ******/

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ont = null;
        try {
            ont = manager.loadOntologyFromOntologyDocument(ontFile);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
        }

        /***** Prepare to get the terms... ******/

        // Set up our reasoner.
        OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
        OWLReasoner reasoner = reasonerFactory.createReasoner(ont);

        // Parser for class expressions.
        DLQueryParser parser = new DLQueryParser(ont, new SimpleShortFormProvider());

        Map<IRI, Set<String>> iriSetMap = null;

        if (topTerm == null){
            iriSetMap = buildIriSynsMap(ont, ontologyID, includeRelated, includeBroad);
        }
        else {
            OWLClassExpression oce = parser.parseClassExpression(topTerm);
            iriSetMap = buildIriSynsMap(ont, ontologyID, getSubClasses(oce, directSubclasses, reasoner), includeRelated, includeBroad);
        }

        /***** Prepare to output... ******/
        // If no outfile, we'll just print to screen.
        if (output == null){
            System.out.println(generateOutput(iriSetMap));
        }
        else {
            try {
                writeSynonymFile(iriSetMap, output);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }
}
