/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.maven.gwthelper.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static org.kie.maven.gwthelper.plugin.utils.ParserUtil.createEmptyDocument;
import static org.kie.maven.gwthelper.plugin.utils.ParserUtil.getString;
import static org.kie.maven.gwthelper.plugin.utils.ParserUtil.getTagAttributes;

/**
 * Check and print out the <b>GWT</b> inheritance tree.
 */
@Mojo(name = "inheritance", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class InheritanceMojo extends AbstractMojo {

    private final static String SRC_MAIN_RESOURCES = "src/main/resources".replace("/", File.separator);
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;
    /**
     * The dependency tree builder to use.
     */
    @Component(hint = "default")
    protected DependencyGraphBuilder dependencyGraphBuilder;
    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;
    /**
     * Whether to fail the build if an inheritance warning is found.
     */
    @Parameter(property = "failOnWarning", defaultValue = "false")
    private boolean failOnWarning;

    /**
     * Whether to have a <b>verbose</b> output
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * Generated file name
     */
    @Parameter(required = false, defaultValue = "inheritance.xml")
    private String fileName;

    /**
     * Whether to write output to file
     */
    @Parameter(property = "fileOutput", defaultValue = "false")
    private boolean fileOutput;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(project.getPackaging())) {
            getLog().info("Skipping pom project");
            return;
        }
        Map<String, Set<String>> missingDeclarationMap = new HashMap<>();
        boolean warning = false;
        if (fileOutput) {
            try {
                Document document = createEmptyDocument();
                Element rootElement = document.createElement("InheritanceTree");
                document.appendChild(rootElement);
                warning = checkInheritance(missingDeclarationMap, document, rootElement);
                String toPrint = getString(document);
                Files.write(Paths.get(fileName), toPrint.getBytes());
            } catch (Exception e) {
                String errorMessage = "Exception " + e.getClass().getName() + " while creating XML Document";
                if (verbose) {
                    getLog().error(errorMessage, e);
                } else {
                    getLog().error(errorMessage);
                }
                throw new MojoFailureException(errorMessage);
            }
        } else {
             warning = checkInheritance(missingDeclarationMap);
        }
        if (!missingDeclarationMap.isEmpty()) {
            getLog().warn("Missing inherited modules");
            missingDeclarationMap.forEach((key, value) -> {
                getLog().warn("*********");
                getLog().warn("Module: " + key);
                value.forEach(s -> getLog().warn("\tmissing: " + s));
            });
        }
        if (warning && failOnWarning) {
            throw new MojoExecutionException("Inheritance problems found");
        }
    }

    /**
     * @return <code>false</code> when no warnings, <code>true</code> otherwise
     * @throws MojoExecutionException
     */
    protected boolean checkInheritance(Map<String, Set<String>> missingDeclarationMap) throws MojoExecutionException {
        Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap = getMavenInheritedGwtModuleArtifactMap();
        Set<File> currentModuleGwtModuleList = currentModuleGwtModules();
        boolean toReturn = false;
        for (File gwtModuleFile : currentModuleGwtModuleList) {
            try {
                toReturn |= printInheritance(gwtModuleFile, inheritedGwtModuleArtifactMap, missingDeclarationMap);
            } catch (Exception e) {
                String errorMessage = "Exception " + e.getClass().getName() + " while printing inheritance of File " + gwtModuleFile.getName();
                if (verbose) {
                    getLog().error(errorMessage, e);
                } else {
                    getLog().error(errorMessage);
                }
                toReturn = true;
            }
        }
        for (JarEntry jarEntry : inheritedGwtModuleArtifactMap.keySet()) {
            try {
                toReturn |= printInheritance(jarEntry, inheritedGwtModuleArtifactMap, missingDeclarationMap);
            } catch (Exception e) {
                String errorMessage = "Exception " + e.getClass().getName() + " while printing inheritance of JarEntry " + jarEntry.getName();
                if (verbose) {
                    getLog().error(errorMessage, e);
                } else {
                    getLog().error(errorMessage);
                }
                toReturn = true;
            }
        }
        return toReturn;
    }

    /**
     * @return <code>false</code> when no warnings, <code>true</code> otherwise
     * @throws MojoExecutionException
     */
    protected boolean checkInheritance(Map<String, Set<String>> missingDeclarationMap, Document document, Element rootElement) throws MojoExecutionException {
        Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap = getMavenInheritedGwtModuleArtifactMap();
        Set<File> currentModuleGwtModuleList = currentModuleGwtModules();
        boolean toReturn = false;
        for (File gwtModuleFile : currentModuleGwtModuleList) {
            try {
                toReturn |= printInheritance(gwtModuleFile, inheritedGwtModuleArtifactMap, missingDeclarationMap, document, rootElement);
            } catch (Exception e) {
                String errorMessage = "Exception " + e.getClass().getName() + " while printing inheritance of File " + gwtModuleFile.getName();
                if (verbose) {
                    getLog().error(errorMessage, e);
                } else {
                    getLog().error(errorMessage);
                }
                toReturn = true;
            }
        }
        for (JarEntry jarEntry : inheritedGwtModuleArtifactMap.keySet()) {
            try {
                toReturn |= printInheritance(jarEntry, inheritedGwtModuleArtifactMap, missingDeclarationMap, document, rootElement);
            } catch (Exception e) {
                String errorMessage = "Exception " + e.getClass().getName() + " while printing inheritance of JarEntry " + jarEntry.getName();
                if (verbose) {
                    getLog().error(errorMessage, e);
                } else {
                    getLog().error(errorMessage);
                }
                toReturn = true;
            }
        }
        return toReturn;
    }

    protected boolean printInheritance(File file, Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap, Map<String, Set<String>> missingDeclarationMap) throws IOException, ParserConfigurationException, SAXException {
        String content = getStringContent(file);
        return printInheritance(content, file.getName().replace(".gwt.xml", "").replace("/", "."), inheritedGwtModuleArtifactMap, missingDeclarationMap);
    }

    protected boolean printInheritance(File file, Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap, Map<String, Set<String>> missingDeclarationMap, Document document, Element rootElement) throws IOException, ParserConfigurationException, SAXException {
        String content = getStringContent(file);
        return printInheritance(content, file.getName().replace(".gwt.xml", "").replace("/", "."), inheritedGwtModuleArtifactMap, missingDeclarationMap, document, rootElement);
    }

    protected boolean printInheritance(JarEntry jarEntry, Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap, Map<String, Set<String>> missingDeclarationMap) throws IOException, ParserConfigurationException, SAXException {
        String content = getContentFromJarEntry(jarEntry, inheritedGwtModuleArtifactMap);
        return printInheritance(content, jarEntry.getName().replace(".gwt.xml", "").replace("/", "."), inheritedGwtModuleArtifactMap, missingDeclarationMap);
    }

    protected boolean printInheritance(JarEntry jarEntry, Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap, Map<String, Set<String>> missingDeclarationMap, Document document, Element rootElement) throws IOException, ParserConfigurationException, SAXException {
        String content = getContentFromJarEntry(jarEntry, inheritedGwtModuleArtifactMap);
        return printInheritance(content, jarEntry.getName().replace(".gwt.xml", "").replace("/", "."), inheritedGwtModuleArtifactMap, missingDeclarationMap, document, rootElement);
    }

    protected boolean printInheritance(String content, String moduleName, Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap, Map<String, Set<String>> missingDeclarationMap) throws IOException, ParserConfigurationException, SAXException {
        List<String> inheritDeclarations = getInheritDeclarations(content);
        commonPrintInheritanceInit(moduleName);
        boolean toReturn = false;
        for (String inheritDeclaration : inheritDeclarations) {
            final Optional<String> mappedArtifactInfo = getMappedArtifactInfo(inheritDeclaration, inheritedGwtModuleArtifactMap);
            toReturn = commonPrintInherit(inheritDeclaration, moduleName, mappedArtifactInfo, missingDeclarationMap);
        }
        return toReturn;
    }

    protected boolean printInheritance(String content, String moduleName, Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap, Map<String, Set<String>> missingDeclarationMap, Document document, Element rootElement) throws IOException, ParserConfigurationException, SAXException {
        List<String> inheritDeclarations = getInheritDeclarations(content);
        commonPrintInheritanceInit(moduleName);
        boolean toReturn = false;
        Element moduleElement = initModuleNode(moduleName, document, rootElement);
        for (String inheritDeclaration : inheritDeclarations) {
            final Optional<String> mappedArtifactInfo = getMappedArtifactInfo(inheritDeclaration, inheritedGwtModuleArtifactMap);
            toReturn = commonPrintInherit(inheritDeclaration, moduleName, mappedArtifactInfo, missingDeclarationMap);
            if (mappedArtifactInfo.isPresent()) {
                addInheritanceToElement(moduleElement, document, inheritDeclaration, mappedArtifactInfo.get());
            } else {
                addMissingInheritanceToElement(moduleElement, document, inheritDeclaration);
            }
        }
        return toReturn;
    }

    protected void commonPrintInheritanceInit(String moduleName) {
        getLog().info("*********");
        getLog().info("Module: " + moduleName);
    }

    protected Optional<String> getMappedArtifactInfo(String inheritDeclaration, Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap) {
        return inheritedGwtModuleArtifactMap.keySet().stream()
                .filter(jarEntry -> {
                    String jarEntryName = jarEntry.getName().replace(".gwt.xml", "").replace("/", ".");
                    return jarEntryName.endsWith(inheritDeclaration);
                })
                .findFirst()
                .map(jarEntry -> {
                         final Artifact artifact = inheritedGwtModuleArtifactMap.get(jarEntry);
                         StringBuilder optional = new StringBuilder();
                         optional.append(artifact.getGroupId());
                         optional.append(":");
                         optional.append(artifact.getArtifactId());
                         optional.append(":");
                         optional.append(artifact.getVersion());
                         return optional.toString();
                     }
                );
    }

    protected boolean commonPrintInherit(String inheritDeclaration, String moduleName, Optional<String> mappedArtifactInfo, Map<String, Set<String>> missingDeclarationMap) {
        boolean toReturn = false;
        String toPrint;
        if (mappedArtifactInfo.isPresent()) {
            toPrint = mappedArtifactInfo.get();
        } else {
            toPrint = "UNKNOWN";
            toReturn = true;
            if (!missingDeclarationMap.containsKey(moduleName)) {
                missingDeclarationMap.put(moduleName, new HashSet<>());
            }
            missingDeclarationMap.get(moduleName).add(inheritDeclaration);
        }
        if (toReturn) {
            getLog().warn("\tinherits " + inheritDeclaration + " from " + toPrint);
        } else {
            getLog().info("\tinherits " + inheritDeclaration + " from " + toPrint);
        }
        return toReturn;
    }

    protected String getContentFromJarEntry(JarEntry jarEntry, Map<JarEntry, Artifact> inheritedGwtModuleArtifactMap) throws IOException {
        JarFile jarFile = new JarFile(inheritedGwtModuleArtifactMap.get(jarEntry).getFile());
        return getStringContent(jarFile, jarEntry);
    }

    protected Element initModuleNode(String moduleName, Document document, Element rootElement) {
        Element toReturn = document.createElement("module");
        Node moduleNode = document.createElement("name");
        moduleNode.setTextContent(moduleName);
        toReturn.appendChild(moduleNode);
        rootElement.appendChild(toReturn);
        return toReturn;
    }

    protected void addInheritanceToElement(Element container, Document document, String gwtModule, String artifactInfo) {
        Node inheritNode = document.createElement("inherit");
        Node moduleNode = document.createElement("gwt-module");
        moduleNode.setTextContent(gwtModule);
        Node artifactNode = document.createElement("artifact");
        artifactNode.setTextContent(artifactInfo);
        inheritNode.appendChild(moduleNode);
        inheritNode.appendChild(artifactNode);
        container.appendChild(inheritNode);
    }

    protected void addMissingInheritanceToElement(Element container, Document document, String gwtModule) {
        Node inheritNode = document.createElement("missing-inherit");
        inheritNode.setTextContent(gwtModule);
        container.appendChild(inheritNode);
    }

    protected String getStringContent(JarFile jarFile, JarEntry toRead) throws IOException {
        InputStream input = jarFile.getInputStream(toRead);
        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(input, Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }
        return textBuilder.toString();
    }

    protected String getStringContent(File toRead) throws IOException {
        return new String(Files.readAllBytes(Paths.get(toRead.getAbsolutePath())));
    }

    protected List<String> getInheritDeclarations(String content) throws
            IOException, SAXException, ParserConfigurationException {
        return getTagAttributes(content, "inherits", "name");
    }

    /**
     * Retrieve a <code>Set</code> with the <b>GWT-modules</b> directly inherited (= declared) inside current module
     * @param
     * @return
     */
    protected Set<File> currentModuleGwtModules() throws MojoExecutionException {
        Set<File> toReturn = new HashSet<>();
        for (String compileSourceRoot : project.getCompileSourceRoots()) {
            populateGwtModuleList(toReturn, compileSourceRoot);
        }
        String resourcePath = project.getBasedir().getAbsolutePath();
        if (!resourcePath.endsWith(File.separator)) {
            resourcePath += File.separator;
        }
        resourcePath += SRC_MAIN_RESOURCES;
        populateGwtModuleList(toReturn, resourcePath);
        return toReturn;
    }

    protected void populateGwtModuleList(Set<File> toPopulate, String parentPath) throws MojoExecutionException {
        File file = new File(parentPath);
        if (!file.exists() || !file.canRead()) {
            throw new MojoExecutionException("Failed to read path " + parentPath);
        }
        populateGwtModuleList(toPopulate, file);
    }

    protected void populateGwtModuleList(Set<File> toPopulate, File parentFile) {
        if (parentFile.isDirectory()) {
            final File[] innerFiles = parentFile.listFiles();
            if (innerFiles != null) {
                for (File innerFile : innerFiles) {
                    populateGwtModuleList(toPopulate, innerFile);
                }
            }
        } else {
            if (parentFile.getName().endsWith(".gwt.xml")) {
                toPopulate.add(parentFile);
            }
        }
    }

    /**
     * Retrieve a <code>Map</code> with the <b>GWT-modules</b> inherited through <b>Maven</b> dependencies
     * @return
     */
    protected Map<JarEntry, Artifact> getMavenInheritedGwtModuleArtifactMap() throws MojoExecutionException {
        Map<JarEntry, Artifact> toReturn = new LinkedHashMap<>();
        DependencyNode dependencyNode = getDependencyNode(session, dependencyGraphBuilder, project);
        try {
            recursivelyReadDependencyNode(toReturn, dependencyNode);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build GwtModuleArtifactMap ", e);
        }
        return toReturn;
    }

    protected void recursivelyReadDependencyNode(Map<JarEntry, Artifact> toPopulate, DependencyNode toRead) throws
            IOException {
        populateGwtModuleArtifactMap(toPopulate, toRead.getArtifact());
        for (DependencyNode dependencyNode : toRead.getChildren()) {
            recursivelyReadDependencyNode(toPopulate, dependencyNode);
        }
    }

    /**
     * Populate a <code>Map</code> with all the <b>GWT-modules</b> found in the given <code>Artifact</code>
     * @param toPopulate
     * @param toRead
     * @throws IOException
     */
    protected void populateGwtModuleArtifactMap(Map<JarEntry, Artifact> toPopulate, Artifact toRead) throws IOException {
        File file = toRead.getFile();
        if (file != null && file.getName().endsWith(".jar")) {
            // optimized solution for the jar case
            JarFile jarFile = new JarFile(file);
            try {
                Enumeration<JarEntry> jarEntries = jarFile.entries();
                while (jarEntries.hasMoreElements()) {
                    final JarEntry jarEntry = jarEntries.nextElement();
                    String entryName = jarEntry.getName();
                    if (entryName.endsWith(".gwt.xml")) {
                        toPopulate.put(jarEntry, toRead);
                    }
                }
            } finally {
                try {
                    jarFile.close();
                } catch (IOException ignore) {
                    // ignore
                }
            }
        }
    }

    /**
     * Retrieve the <code>DependencyNode</code> of the given <code>MavenProject</code>
     * @param session
     * @param dependencyGraphBuilder
     * @param project
     * @return
     * @throws MojoExecutionException
     */
    protected DependencyNode getDependencyNode(MavenSession session, DependencyGraphBuilder
            dependencyGraphBuilder, MavenProject project) throws MojoExecutionException {
        try {
            ProjectBuildingRequest buildingRequest =
                    new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            buildingRequest.setProject(project);
            // non-verbose mode use dependency graph component, which gives consistent results with Maven version
            // running
            return dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
        } catch (DependencyGraphBuilderException exception) {
            throw new MojoExecutionException("Cannot build project dependency graph", exception);
        }
    }
}
