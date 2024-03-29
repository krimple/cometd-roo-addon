package org.sillyweasel.rooaddons.cometd;

import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.TypeManagementService;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetailsBuilder;
import org.springframework.roo.classpath.details.MemberFindingUtils;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.project.*;
import org.springframework.roo.support.util.WebXmlUtils;
import org.springframework.roo.support.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of operations this add-on offers.
 *
 * @since 1.1
 */
@Component // Use these Apache Felix annotations to register your commands class in the Roo container
@Service
public class CometdOperationsImpl implements CometdOperations {

  private static final String WEB_XML = "WEB-INF/web.xml";


  @Reference
  private FileManager fileManager;

  @Reference
  private PathResolver pathResolver;


  /**
   * Use ProjectOperations to install new dependencies, plugins, properties, etc into the project configuration
   */
  @Reference
  private ProjectOperations projectOperations;

  /**
   * Use TypeLocationService to find types which are annotated with a given annotation in the project
   */
  @Reference
  private TypeLocationService typeLocationService;

  /**
   * Use TypeManagementService to change types
   */
  @Reference
  private TypeManagementService typeManagementService;

  /**
   * {@inheritDoc}
   */
  public boolean isSetupAvailable() {
    // Check if a project has been created
    return true;
  }

  public boolean isRemoveAvailable() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void annotateType(JavaType javaType) {
    // Use Roo's Assert type for null checks
    Validate.notNull(javaType, "Java type required");

    // Obtain ClassOrInterfaceTypeDetails for this java type
    ClassOrInterfaceTypeDetails existing = typeLocationService.getTypeDetails(javaType);

    // Test if the annotation already exists on the target type
    if (existing != null && MemberFindingUtils.getAnnotationOfType(existing.getAnnotations(), new JavaType(RooCometd.class.getName())) == null) {
      ClassOrInterfaceTypeDetailsBuilder classOrInterfaceTypeDetailsBuilder = new ClassOrInterfaceTypeDetailsBuilder(existing);

      // Create JavaType instance for the add-ons trigger annotation
      JavaType rooRooCometd = new JavaType(RooCometd.class.getName());

      // Create Annotation metadata
      AnnotationMetadataBuilder annotationBuilder = new AnnotationMetadataBuilder(rooRooCometd);

      // Add annotation to target type
      classOrInterfaceTypeDetailsBuilder.addAnnotation(annotationBuilder.build());

      // Save changes to disk
      typeManagementService.createOrUpdateTypeOnDisk(classOrInterfaceTypeDetailsBuilder.build());
    }
  }

  /**
   * {@inheritDoc}
   */
  public void annotateAll() {
    // Use the TypeLocationService to scan project for all types with a specific annotation
    for (JavaType type : typeLocationService.findTypesWithAnnotation(new JavaType("org.springframework.roo.addon.javabean.RooJavaBean"))) {
      annotateType(type);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void setup() {

    // shamelessly lifted from the controller add-on
    // see WebMvcOperationsImpl.java
    Validate.isTrue(projectOperations.isFocusedProjectAvailable(),
        "Project metadata required");

    // Verify that the web.xml already exists
    final String webXmlPath = pathResolver.getFocusedIdentifier(
        Path.SRC_MAIN_WEBAPP, WEB_XML);
    Validate.isTrue(fileManager.exists(webXmlPath), "'" + webXmlPath
        + "' does not exist");

    final Document document = XmlUtils.readXml(fileManager
        .getInputStream(webXmlPath));

    // now we have xml in memory, manipulate...

    document.getDocumentElement().setAttribute("version", "3.0");

    // add servlets and params for cometd

    WebXmlUtils.addServlet("cometd", "org.cometd.server.CometdServlet",
        "/cometd/*", 1,
        document, null,
        new WebXmlUtils.WebXmlParam("timeout", "60000"),
        new WebXmlUtils.WebXmlParam("logLevel", "3"),
        new WebXmlUtils.WebXmlParam("transports", "org.cometd.websocket.server.WebSocketTransport"));

    WebXmlUtils.addFilter("cross-origin", "org.eclipse.jetty.servlets.CrossOriginFilter",
        "/cometd/*", document, null);


    // fixup servlet for async-supported
    Element servlet = XmlUtils.findFirstElement("//servlet-class[.='org.cometd.server.CometdServlet']/..", document);
    servlet.appendChild(getAsyncTag(document, servlet));

    // fixup filter for async-supported
    Element filter = XmlUtils.findFirstElement("//filter-class[.='org.eclipse.jetty.servlets.CrossOriginFilter']/..", document);
    filter.appendChild(getAsyncTag(document, filter));

    fileManager.createOrUpdateTextFileIfRequired(webXmlPath,
        XmlUtils.nodeToString(document), true);

    List<Dependency> dependencies = new ArrayList<Dependency>();

    for (Element dependencyElement : XmlUtils.findElements("/configuration/maven/dependencies/dependency", XmlUtils.getConfiguration(getClass()))) {
      dependencies.add(new Dependency(dependencyElement));

    }

    String moduleName = projectOperations.getFocusedModuleName();
    projectOperations.addDependencies(moduleName, dependencies);

    List<Plugin> plugins = new ArrayList<Plugin>();

    for (Element pluginElement : XmlUtils.findElements("/configuration/maven/build/plugins/plugin",
        XmlUtils.getConfiguration(getClass()))) {
      plugins.add(new Plugin(pluginElement));
    }

    // search for and remove existing plugin for war plugin - should be JIRA, but Roo won't overwrite an existing one
    // and fails silently. Should have a MUTABLE set of Maven objects, and they should allow an update of a plugin.
    Plugin warPlugin = new Plugin("org.apache.maven.plugins", "maven-war-plugin", "2.1.1");

    if (projectOperations.getFocusedModule().isPluginRegistered(warPlugin.getGAV())) {
        projectOperations.removeBuildPlugin(moduleName, new Plugin("org.apache.maven.plugins", "maven-war-plugin", "2.1.1"));
      projectOperations.removeBuildPlugin(moduleName, warPlugin);
    }

    // now, add back in the plugins.
    projectOperations.addBuildPlugins(moduleName, plugins);
  }

  private Element getAsyncTag(Document document, Element servlet) {
    Element asyncSupport = document.createElement("asynch-supported");
    asyncSupport.setTextContent("true");

    servlet.appendChild(asyncSupport);
    return asyncSupport;
  }

  public void remove() {

    // shamelessly lifted from the controller add-on
    // see WebMvcOperationsImpl.java
    Validate.isTrue(projectOperations.isFocusedProjectAvailable(),
        "Project metadata required");

    // Verify that the web.xml already exists
    final String webXmlPath = pathResolver.getFocusedIdentifier(
        Path.SRC_MAIN_WEBAPP, WEB_XML);
    Validate.isTrue(fileManager.exists(webXmlPath), "'" + webXmlPath
        + "' does not exist");

    final Document document = XmlUtils.readXml(fileManager
        .getInputStream(webXmlPath));

    Element documentElement = document.getDocumentElement();

    // now we have xml in memory, manipulate...
    documentElement.setAttribute("version", "2.5");


    Element servlet = XmlUtils.findFirstElement("//servlet-class[.='org.cometd.server.CometdServlet']/..", document);
    Element servletMapping = XmlUtils.findFirstElement("//servlet-mapping/servlet-name[.='cometd']/..", document);

    if (servlet != null) {
      documentElement.removeChild(servlet);
    }

    if (servletMapping != null) {
      documentElement.removeChild(servletMapping);
    }
    // note - don't do this - will not find it - document.removeChild(servletMapping);

    Element filter = XmlUtils.findFirstElement("//filter-class[.='org.eclipse.jetty.servlets.CrossOriginFilter']/..", document);
    if (filter != null) {
      documentElement.removeChild(filter);
    }

    Element filterMapping = XmlUtils.findFirstElement("//filter-mapping/filter-name[.='cross-origin']/..", document);
    if (filterMapping != null) {
      documentElement.removeChild(filterMapping);
    }

    fileManager.createOrUpdateTextFileIfRequired(webXmlPath,
        XmlUtils.nodeToString(document), true);

    List<Dependency> dependencies = new ArrayList<Dependency>();

    for (Element dependencyElement : XmlUtils.findElements("/configuration/maven/dependencies/dependency", XmlUtils.getConfiguration(getClass()))) {
      // TODO - how to guard this if dependency isn't already there??
      dependencies.remove(new Dependency(dependencyElement));
    }

    String moduleName = projectOperations.getFocusedModuleName();
    projectOperations.addDependencies(moduleName, dependencies);

    List<Plugin> plugins = new ArrayList<Plugin>();

    for (Element pluginElement : XmlUtils.findElements("/configuration/maven/build/plugins/plugin",
        XmlUtils.getConfiguration(getClass()))) {
      // TODO - how to guard this if plugin isn't already there??
      plugins.remove(new Plugin(pluginElement));
    }
  }
}