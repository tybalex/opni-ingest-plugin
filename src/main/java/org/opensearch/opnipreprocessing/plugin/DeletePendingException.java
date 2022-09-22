package org.opensearch.opnipreprocessing.plugin;

public class DeletePendingException extends RuntimeException {
    public DeletePendingException(String id) {
        super("cluster " + id + " is pending deletion");
    }
}
