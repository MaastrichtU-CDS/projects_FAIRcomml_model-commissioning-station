package nl.maastrichtuniversity.cds.modelcommissioningstation.services;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryResult;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    }

    /**
     * Reload the index by removing the index triples from the RDF store,
     * and loading them again from the standard remote location.
     */
    public void reloadIndex() {
        this.logger.info("Clear and reload index");
        this.conn.clear(SimpleValueFactory.getInstance().createIRI(IndexService.INDEX_URL));
        this.addRemoteFile(IndexService.INDEX_URL);
    }

    /**
     * Add a remote location to the current repository
     * @param remoteLocation: string representation of the URL where the turtle file is located
     */
    private void addRemoteFile(String remoteLocation) {
        this.conn.prepareUpdate("LOAD <" + remoteLocation + "> INTO GRAPH <" + remoteLocation + ">").execute();
    }

    private void fetchReferencedFiles() {
        IRI predicateToSearch = SimpleValueFactory.getInstance().createIRI("https://fairmodels.org/ontology.owl#referencedInformation");
        RepositoryResult<Statement> statements = this.conn.getStatements(null, predicateToSearch,null);

        while(statements.hasNext()) {
            Statement stmt = statements.next();
            logger.debug(stmt.getSubject().toString() + " | " +
                    stmt.getPredicate().toString() + " | " +
                    stmt.getObject().toString());
            this.addRemoteFile(stmt.getObject().stringValue());
        }
    }

}