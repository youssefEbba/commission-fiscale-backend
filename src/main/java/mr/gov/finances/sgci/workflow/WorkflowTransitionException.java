package mr.gov.finances.sgci.workflow;

public class WorkflowTransitionException extends RuntimeException {

    public WorkflowTransitionException(String message) {
        super(message);
    }

    public WorkflowTransitionException(String from, String to, String entity) {
        super(String.format("Transition non autorisée pour %s: %s → %s", entity, from, to));
    }
}
