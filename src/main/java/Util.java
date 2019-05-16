import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.mansyntax.ManchesterOWLSyntaxParser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Util {

    // Some ontologies aren't using their assigned prefix, so this maps them properly.
    public static Map<String, String> prefixMap;

    static {
        prefixMap = new HashMap<>();
        prefixMap.put("epo", "epidemiology_ontology");
        prefixMap.put("ncit", "thesaurus");
    }

    public static boolean isBaseOntologyClass(OWLClass c, String ontologyId){
        ontologyId = (prefixMap.get(ontologyId.toLowerCase()) == null ? ontologyId : prefixMap.get(ontologyId.toLowerCase()));

        String termIRI = c.getIRI().getShortForm();
        String fullIRI = c.getIRI().toString();
        String termPrefix;
        if (termIRI.contains("_") && fullIRI.lastIndexOf("#") == -1)
            termPrefix = termIRI.substring(0, termIRI.indexOf("_"));
        else if (fullIRI.lastIndexOf("#") != -1){
            int hash = fullIRI.lastIndexOf("#");
            String endString = fullIRI.substring(0, hash);
            termPrefix = endString.substring(endString.lastIndexOf("/")+1).replace(".owl", "");
        }
        else termPrefix = termIRI;

        return termPrefix.equalsIgnoreCase(ontologyId);
    }

    public static boolean isDeprecatedClass(OWLClass c, OWLOntology o){
        if(c.getIRI().getShortForm().contains("ObsoleteClass")) return true;

        for(OWLAnnotationAssertionAxiom a : o.getAnnotationAssertionAxioms(c.getIRI())) {
            if(a.getProperty().isDeprecated()){
                if(a.getValue() instanceof OWLLiteral &&
                        ((OWLLiteral)a.getValue()).getLiteral().equals("true"))
                    return true;
                return false;
            }
        }
        return false;
    }

    // An axiom is deprecated if it involves any deprecated class.
    public static boolean isDeprecatedAxiom(OWLAxiom a, OWLOntology o){
        return a.classesInSignature().anyMatch(c -> isDeprecatedClass(c, o));
    }

    public static OWLOntology loadOntology(OWLOntologyManager manager, String uri){
        IRI documentIRI = IRI.create(uri);

        try {
            return manager.loadOntologyFromOntologyDocument(documentIRI);
        } catch (OWLOntologyCreationException e) {
            e.printStackTrace();
            return null;
        } catch (UnloadableImportException e){
            e.printStackTrace();
            return null;
        }
    }

    private Util(){}
}
