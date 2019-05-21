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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.StringUtils;

/**
 * Utility class used to contain shared methods
 */
public abstract class AbstractKieGwtHelperMojo extends AbstractMojo {

    protected final static String SRC_MAIN_JAVA = "/src/main/java".replace("/", File.separator);
    protected final static String SRC_MAIN_RESOURCES = "/src/main/resources".replace("/", File.separator);

    /**
     * Comma-separated additional source directories.
     */
    @Parameter(name="rootDirectories", required = true)
    protected String rootDirectories;

    /**
     * Comma-separated pattern to match for including modules.
     * Does not use regex, but simple string
     */
    @Parameter(name="includes", required = false)
    protected String includes;

    /**
     * Comma-separated pattern to match for excluding modules.
     * Does not use regex, but simple string
     */
    @Parameter(name="excludes", required = false)
    protected String excludes;

    @Parameter(readonly = true, defaultValue = "${project}")
    protected MavenProject project;

    /**
     * Charset encoding of the source files.  Defaults to UTF-8
     */
    @Parameter(defaultValue = "${project.build.sourceEncoding}")
    protected String encoding;

    /**
     * Method to retrieve  <b>src/main/java</b> <b>src/main/resources</b> directories of valid <b>gwt</b> ones, i.e. directories containing a <b>/src/main/resources/(..)/(..).gwt.xml</b> file,
     * matching the (eventually) given <b>includes/excludes</b> patterns and <b>root</b> directories
     *
     * @return <code>Set&lt;File&gt;</code> with found directories
     * @throws MojoExecutionException
     */
    protected Set<File> getValidGWTModulesSources() throws MojoExecutionException {
        if (StringUtils.isNotEmpty(includes) && StringUtils.isNotEmpty(excludes)) {
            throw new MojoExecutionException("Only one of 'includes' or 'excludes' can be provided");
        }
        String[] rootDirectoryPaths = rootDirectories.split(",");
        Set<File> toReturn = new HashSet<>();
        for (String rootDirectoryPath : rootDirectoryPaths) {
            File rootDirectory = new File(rootDirectoryPath);
            loopForMavenModules(rootDirectory, toReturn);
        }
        return toReturn;
    }

    /**
     * Method to recursively <i>filter</i> only <b>maven</b> directories, i.e. directories containing a <b>pom.xml</b> file
     * @param source
     * @param validGWTModules
     * @throws MojoExecutionException
     */
    protected void loopForMavenModules(File source, Set<File> validGWTModules) throws MojoExecutionException {
        checkReadableDirectory(source);
        if (isMavenModule(source)) {
            getLog().debug("mavenModule " + source.getAbsolutePath());
            loopForGwtModule(source, validGWTModules);
            List<File> sources = Arrays.asList(source.listFiles());
            for (File file : sources) {
                if (file.isDirectory()) {
                    loopForMavenModules(file, validGWTModules);
                }
            }
        }
    }

    /**
     * Method to recursively <i>filter</i> only <b>gwt</b> directories, i.e. directories containing a <b>/src/main/resources/(..)/(..).gwt.xml</b> file,
     * and add their <b>src/main/java</b> <b>src/main/resources</b> directories to given <code>Set&lt;File&gt;</code>
     * @param source
     * @param validGWTModulesSources
     * @throws MojoExecutionException
     */
    protected void loopForGwtModule(File source, Set<File> validGWTModulesSources) throws MojoExecutionException {
        getLog().debug("loopForGwtModule " + source.getAbsolutePath());
        if (isValidGwtModule(source)) {
            File sources = new File(source.getAbsolutePath() + SRC_MAIN_JAVA);
            checkReadableDirectory(sources);
            validGWTModulesSources.add(sources);
            File resources = new File(source.getAbsolutePath() + SRC_MAIN_RESOURCES);
            checkReadableDirectory(resources);
            validGWTModulesSources.add(resources);
        }
    }

    /**
     * Method to check if in the given directory is a <i>valid</i> <b>Gwt</b> module, i.e. it contains a "src/main/resources/./.gwt.xml" file
     * eventually matching the <b>includes/excludes</b> patterns
     * @param toCheck
     * @return <code>true</code> if the given directory contains a <b>Gwt</b> module, <code>false</code> otherwise
     * @throws MojoExecutionException
     */
    protected boolean isValidGwtModule(File toCheck) throws MojoExecutionException {
        getLog().debug("isValidGwtModule " + toCheck.getAbsolutePath());
        File resources = new File(toCheck.getAbsolutePath() + SRC_MAIN_RESOURCES);
        return !getValidModuleFilePaths(resources).isEmpty();
    }

    /**
     * Retrieve the <code>List&lt;Path&gt;</code> of all valid GWT modules, or an <b>empty</b> one if none is found
     * @param resourcesFile The <code>File</code> pointing to the <b>src/main/resources</b> directory
     * @return the <code>List&lt;Path&gt;</code> with all valid <code>Path</code>s
     */
    protected List<Path> getValidModuleFilePaths(File resourcesFile) throws MojoExecutionException {
        if (!resourcesFile.exists()) {
            return Collections.emptyList();
        }
        try {
            return Files.walk(Paths.get(resourcesFile.getAbsolutePath()))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".gwt.xml"))
                    .filter(first -> {
                        String fileName = first.getFileName().toString();
                        if (StringUtils.isNotEmpty(includes)) {
                            return matchPattern(fileName, includes);
                        } else if (StringUtils.isNotEmpty(excludes)) {
                            return !matchPattern(fileName, excludes);
                        } else {
                            return true;
                        }
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            String errorMessage = StringUtils.isEmpty(e.getMessage()) ? e.getClass().getName() : e.getMessage();
            errorMessage += " while analyzing " + resourcesFile.getAbsolutePath();
            throw new MojoExecutionException(errorMessage);
        }
    }

    /**
     * Method to check if the given directory is a <b>Maven</b> module (it contains <b>pom.xml</b>)
     * @param toCheck
     * @return <code>true</code> if the given directory contains a <b>Maven</b> module, <code>false</code> otherwise
     */
    protected boolean isMavenModule(File toCheck) {
        return toCheck.isDirectory() && toCheck.list() != null && Arrays.asList(toCheck.list()).contains("pom.xml");
    }

    /**
     * Method to check if the given String contains one of the comma-separated pattern.
     * Matching is done with String.contains()
     * @param toCheck
     * @param pattern
     * @return <code>true</code> if the <b>toCheck</b> String contains the <b>pattern</b> one, <code>false</code> otherwise
     */
    protected boolean matchPattern(String toCheck, String pattern) {
        return Arrays.stream(pattern.split(",")).anyMatch(toCheck::contains);
    }

    /**
     * Method to check if the given file is an <b>existing, readable, directory</b>
     * @param toCheck
     * @throws MojoExecutionException if check fails
     */
    protected void checkReadableDirectory(File toCheck) throws MojoExecutionException {
        if (!toCheck.exists() || !toCheck.canRead() || !toCheck.isDirectory()) {
            throw new MojoExecutionException("Directory " + toCheck.getAbsolutePath() + " is not a readable directory");
        }
    }
}
