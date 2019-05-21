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
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Add more source directories to the POM.
 */
@Mojo(name = "add-source", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class AddSourceMojo extends AbstractKieGwtHelperMojo {

    public void execute() throws MojoExecutionException {
        Set<File> validGWTModulesSources = getValidGWTModulesSources();
        addGwtModulesSources(validGWTModulesSources);
    }

    /**
     * Method to add valid module' sources to current <b>project</b> compile sources
     * @param validGWTModulesSources
     * @throws MojoExecutionException
     */
    private void addGwtModulesSources(Set<File> validGWTModulesSources) throws MojoExecutionException {
        getLog().debug("addGwtModulesSources " + validGWTModulesSources.size());
        validGWTModulesSources.forEach(source -> {
            this.project.addCompileSourceRoot(source.getAbsolutePath());
            if (getLog().isInfoEnabled()) {
                getLog().info("Source directory: " + source + " added.");
            }
        });
    }
}
