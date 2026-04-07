package mr.gov.finances.sgci.workflow;

import lombok.Getter;

@Getter
public class WorkflowTransitionException extends RuntimeException {

    public static final String DEFAULT_CODE = "WORKFLOW_TRANSITION_INVALID";

    private final String code;

    public WorkflowTransitionException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
    }

    public WorkflowTransitionException(String code, String message) {
        super(message);
        this.code = code != null ? code : DEFAULT_CODE;
    }

    public WorkflowTransitionException(String from, String to, String entity) {
        super(String.format("Transition non autorisée pour %s: %s → %s", entity, from, to));
        this.code = DEFAULT_CODE;
    }
}
