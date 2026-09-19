package com.claire.claims.common;

/** Application exceptions, each mapping to one HTTP status. */
public final class ApiExceptions {

    private ApiExceptions() { }

    /** 404 - the addressed resource does not exist. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String entity, Object id) {
            super(entity + " " + id + " was not found");
        }
        public NotFoundException(String message) { super(message); }
    }

    /** 409 - the request is well formed but conflicts with current state. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    /** 400 - the request breaks a business rule that Bean Validation cannot express. */
    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) { super(message); }
    }

    /** 501 - a Phase 2 feature that is deliberately not built yet. */
    public static class NotImplementedYetException extends RuntimeException {
        private final String handoffRef;
        public NotImplementedYetException(String feature, String handoffRef) {
            super(feature + " is not implemented in Phase 1. "
                  + "See docs/PHASE2_HANDOFF.md section " + handoffRef + ".");
            this.handoffRef = handoffRef;
        }
        public String getHandoffRef() { return handoffRef; }
    }
}
