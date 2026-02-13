package de.materna.structurizr.renderer;

/**
 * Exception for issues during the rendering of the Structurizr DSL file.
 *
 * @author Stephan Pirnbaum
 */
public class StructurizrRenderingException extends Exception {
    public StructurizrRenderingException(String message) {
        super(message);
    }

    public StructurizrRenderingException(String message, Throwable cause) {
        super(message, cause);
    }


}
