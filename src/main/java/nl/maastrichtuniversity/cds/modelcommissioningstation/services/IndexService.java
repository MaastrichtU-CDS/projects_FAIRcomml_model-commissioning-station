package nl.maastrichtuniversity.cds.modelcommissioningstation.services;

import nl.maastrichtuniversity.cds.modelcommissioningstation.model.*;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndexService {
    public static final String INDEX_URL = "https://fairmodels.org/index.ttl";
    private final Repository repo;
    private final RepositoryConnection conn;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public IndexService() {
        this.repo = new SailRepository(new MemoryStore());
        this.repo.init();
        this.conn = repo.getConnection();

        this.reloadIndex();
        this.fetchReferencedFiles();
        logger.info("Done loading all models");
    }

    /**
     * Reload the index by removing the index triples from the RDF store,
     * and loading them again from the standard remote location.
     */
    public void reloadIndex() {
        this.logger.info("Clear and reload index");
        this.conn.clear(SimpleValueFactory.getInstance().createIRI(IndexService.INDEX_URL));
        try {
            this.addRemoteFile(IndexService.INDEX_URL);
        } catch (IOException e) {
            logger.warn("Index URL is incorrect/malformed (" + IndexService.INDEX_URL + ")");
        }
    }

    /**
     * Add a remote location to the current repository
     * @param remoteLocation: string representation of the URL where the turtle file is located
     */
    public void addRemoteFile(String remoteLocation) throws IOException {
        IRI graphIRI = SimpleValueFactory.getInstance().createIRI(remoteLocation);
        URL documentURL = new URL(remoteLocation);
        RDFFormat format = Rio.getParserFormatForFileName(documentURL.toString()).orElse(RDFFormat.RDFXML);
        org.eclipse.rdf4j.model.Model results = Rio.parse(documentURL.openStream(), remoteLocation, format);

        this.conn.add(results, graphIRI);
    }

    /**
     * Search for the referenced files, as indicated in the index turtle file.
     * Remote files are added to the current in-memory RDF store.
     */
    private void fetchReferencedFiles() {
        IRI predicateToSearch = SimpleValueFactory.getInstance().createIRI("https://fairmodels.org/ontology.owl#referencedInformation");
        RepositoryResult<Statement> statements = this.conn.getStatements(null, predicateToSearch,null);

        while(statements.hasNext()) {
            Statement stmt = statements.next();
            logger.debug(stmt.getSubject().toString() + " | " +
                    stmt.getPredicate().toString() + " | " +
                    stmt.getObject().toString());
            try {
                this.addRemoteFile(stmt.getObject().stringValue());
            } catch (IOException e) {
                logger.warn("Could not load/find referenced file " + stmt.getObject().stringValue(), e);
            }
        }
    }

    /**
     * Retrieve the URI and human-readable label of all available models
     * @return Map containing the URI as key, and the label as value.
     */
    public Map<String,String> getAllModels() {
        String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> " +
        "PREFIX fml: <https://fairmodels.org/ontology.owl#> " +
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
        "SELECT ?model ?label " +
        "WHERE { " +
	        "?model rdf:type fml:Model. " +
            "?model rdfs:label ?label. " +
        "}";

        Map<String, String> retResult = new HashMap<String, String>();
        TupleQueryResult result = this.conn.prepareTupleQuery(query).evaluate();
        while(result.hasNext()) {
            BindingSet bs = result.next();
            retResult.put(bs.getValue("model").stringValue(), bs.getValue("label").stringValue());
        }

        return retResult;
    }

    private List<IRI> getClassTypesForUri(IRI uri) {
        RepositoryResult<Statement> statementsType = this.conn.getStatements(uri, RDF.TYPE, null);
        List<IRI> classTypes = new ArrayList<IRI>();
        while(statementsType.hasNext()) {
            classTypes.add((IRI)statementsType.next().getObject());
        }

        return classTypes;
    }

    public RdfRepresentation getObjectForUri(IRI uri) {
        RdfRepresentation returnObject = null;

        List<IRI> classTypes = this.getClassTypesForUri(uri);
        RepositoryResult<Statement> statementsModel = this.conn.getStatements(uri, null, null);
        List<Statement> allStatements = new ArrayList<Statement>();
        while(statementsModel.hasNext()) {
            allStatements.add(statementsModel.next());
        }

        if (classTypes.contains(Model.CLASS_URI)) {
            returnObject = new Model(uri, allStatements, this);
        }

        if (classTypes.contains(Prediction.CLASS_URI)) {
            returnObject = new Prediction(uri, allStatements, this);
        }

        if (classTypes.contains(InformationElement.CLASS_URI)) {
            returnObject = new InformationElement(uri, allStatements, this);
        }

        if (returnObject == null) {
            returnObject = new SimpleRdfRepresentation(uri, allStatements, this);
        }

        return returnObject;
    }

    public RdfRepresentation getObjectForUri(String uri) {
        return getObjectForUri(SimpleValueFactory.getInstance().createIRI(uri));
    }

}
