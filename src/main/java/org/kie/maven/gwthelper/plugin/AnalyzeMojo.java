/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates.
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
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.kie.maven.gwthelper.plugin.model.GWTModule;

import static org.kie.maven.gwthelper.plugin.utils.ModuleReader.readGwtModule;

/**
 * Recursively scanning GWT modules to find missing inherited ones.
 */
@Mojo(name = "analyze", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class AnalyzeMojo extends AbstractKieGwtHelperMojo {

    /**
     * The entry point to Maven Artifact Resolver, i.e. the component doing all the work.
     */
    @Component
    private RepositorySystem repoSystem;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    /**
     * List of remote repositories
     */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<File> validGWTModulesSources = getValidGWTModulesSources();
        List<File> dependenciesFiles = getDependenciesFiles();
        analyzeValidGWTModulesSources(validGWTModulesSources, dependenciesFiles);
    }

    /**
     * Method to recursively analyze valid gwt modules for missing inherited ones
     * @param validGWTModulesSources all the valid gwt modules sources
     */
    private void analyzeValidGWTModulesSources(Set<File> validGWTModulesSources, List<File> dependenciesFiles) throws MojoExecutionException {
        getLog().debug("analyzeValidGWTModulesSources " + validGWTModulesSources);
        Set<Path> modulesPath = new HashSet<>();
        for(File validGWTModule : validGWTModulesSources) {
            if (validGWTModule.getAbsolutePath().endsWith(SRC_MAIN_RESOURCES)) {
                modulesPath.addAll(getValidModuleFilePaths(validGWTModule));
            }
        }
        analyzeModules(modulesPath, dependenciesFiles);
    }

    /**
     * Method to recursively analyze valid gwt module files for missing inherited ones
     * @param toAnalyze
     */
    private void analyzeModules(Set<Path> toAnalyze, List<File> dependenciesFiles) throws MojoExecutionException {
        getLog().debug("analyzeModules " + toAnalyze);
        for(Path modulePath : toAnalyze) {
            try {
                GWTModule gwtModule = readGwtModule(modulePath, getLog());
                getLog().debug(gwtModule.toString());
                analyzeInherits(gwtModule.getInherits(), dependenciesFiles);
            } catch (Exception e) {
                String errorMessage = StringUtils.isEmpty(e.getMessage()) ? e.getClass().getName() : e.getMessage();
                errorMessage += " while analyzing " + modulePath.toString();
                throw new MojoExecutionException(errorMessage);
            }
        }
    }

    private void analyzeInherits(List<String> toAnalyze, List<File> dependenciesFiles) throws MojoFailureException, MojoExecutionException {
        getLog().debug("analyzeInherits " + toAnalyze);
        for(File dependencyFile : dependenciesFiles) {
            try {
                this.project.getClassRealm().findResource(toAnalyze.get(0))
                FileSystem fileSystem = FileSystems.newFileSystem(dependencyFile.toURI(), Collections.emptyMap());
                Path myPath = fileSystem.getPath("/resources");
                Stream<Path> walk = Files.walk(myPath, 1);
                for (Iterator<Path> it = walk.iterator(); it.hasNext();){
                    getLog().debug(it.next().toString());
                }
            } catch (IOException e) {
                String errorMessage = StringUtils.isEmpty(e.getMessage()) ? e.getClass().getName() : e.getMessage();
                errorMessage += " while reading " + dependencyFile.toString();
                throw new MojoExecutionException(errorMessage);
            }


        }


    }

    private List<File> getDependenciesFiles() throws MojoFailureException, MojoExecutionException {
        getLog().debug("getDependenciesFiles");
        final List<Dependency> dependencies = project.getDependencies();
        List<File> toReturn = new ArrayList<>();
        for(Dependency dependency : dependencies) {
            toReturn.add(getArtifactFile(dependency));
        }
        return toReturn;
    }

    private File getArtifactFile(Dependency dependency) throws MojoFailureException, MojoExecutionException {
        Artifact artifact;
        try {
            String artifactCoords = dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getType();
            artifactCoords += StringUtils.isEmpty(dependency.getClassifier()) ? ":" + dependency.getVersion() : ":" + dependency.getClassifier() + ":" + dependency.getVersion();
            artifact = new DefaultArtifact(artifactCoords);
        } catch (IllegalArgumentException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);
        request.setRepositories(remoteRepos);
        getLog().info("Resolving artifact " + artifact + " from " + remoteRepos);
        ArtifactResult result;
        try {
            result = repoSystem.resolveArtifact(repoSession, request);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        getLog().info("Resolved artifact " + artifact + " to " + result.getArtifact().getFile() + " from  " + result.getRepository());
        return result.getArtifact().getFile();
    }



}
