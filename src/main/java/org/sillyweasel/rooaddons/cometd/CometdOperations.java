package org.sillyweasel.rooaddons.cometd;

import org.springframework.roo.model.JavaType;

/**
 * Interface of operations this add-on offers. Typically used by a command type or an external add-on.
 *
 * @since 1.1
 */
public interface CometdOperations {


    /**
     * Annotate the provided Java type with the trigger of this add-on
     */
    void annotateType(JavaType type);
    
    /**
     * Annotate all Java types with the trigger of this add-on
     */
    void annotateAll();
    
    /**
     * Setup all add-on artifacts (dependencies in this case)
     */
    void setup();

    void remove();

  boolean isSetupAvailable();
  boolean isRemoveAvailable();
}