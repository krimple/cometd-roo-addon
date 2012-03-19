package org.sillyweasel.rooaddons.cometd;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.shell.CliAvailabilityIndicator;
import org.springframework.roo.shell.CliCommand;
import org.springframework.roo.shell.CommandMarker;

/**
 * Sample of a command class. The command class is registered by the Roo shell following an
 * automatic classpath scan. You can provide simple user presentation-related logic in this
 * class. You can return any objects from each method, or use the logger directly if you'd
 * like to emit messages of different severity (and therefore different colours on
 * non-Windows systems).
 *
 * @since 1.1
 */
@Component // Use these Apache Felix annotations to register your commands class in the Roo container
@Service
public class CometdCommands implements CommandMarker { // All command types must implement the CommandMarker interface

  /**
   * Get a reference to the CometdOperations from the underlying OSGi container
   */
  @Reference
  private CometdOperations operations;

  /**
   * This method is optional. It allows automatic command hiding in situations when the command should not be visible.
   * For example the 'entity' command will not be made available before the user has defined his persistence settings
   * in the Roo shell or directly in the project.
   * <p/>
   * You can define multiple methods annotated with {@link CliAvailabilityIndicator} if your commands have differing
   * visibility requirements.
   *
   * @return true (default) if the command should be visible at this stage, false otherwise
   */
  @CliAvailabilityIndicator({"cometd setup"})
  public boolean isSetupCommandAvailable() {
    return operations.isSetupAvailable();
  }

  @CliAvailabilityIndicator({"cometd remove"})
  public boolean isRemoveCommandAvailable() {
    return operations.isRemoveAvailable();
  }

  /**
   * This method registers a command with the Roo shell. It has no command attribute.
   */
  @CliCommand(value = "cometd setup", help = "Setup Cometd addon")
  public void setup() {
    operations.setup();
  }

  @CliCommand(value = "cometd remove", help = "Remove Cometd addon")
  public void remove() {
    operations.remove();
  }

}