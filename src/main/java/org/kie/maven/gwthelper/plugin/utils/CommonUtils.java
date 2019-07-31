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
package org.kie.maven.gwthelper.plugin.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Utility class
 */
public class CommonUtils {

    /**
     * Return the full content of a <code>Path</code>
     * @param path
     * @param charset
     * @return
     * @throws IOException
     */
    public static String readPathFully(Path path, Charset charset) throws IOException {
        return readFileFully(path.toFile(), charset);
    }

    /**
     * Return the full content of a <code>File</code>
     * @param file
     * @param charset
     * @return
     * @throws IOException
     */
    public static String readFileFully(File file, Charset charset) throws IOException {
        byte[] encoded = Files.readAllBytes(file.toPath());
        return new String(encoded, charset);
    }

//    public static Map<String, File> getArtifactFiles(RepositorySystem repoSystem, RepositorySystemSession repoSession, List<RemoteRepository> remoteRepos) throws MojoFailureException, MojoExecutionException {
//        Map<String, File> toReturn = new HashMap<>();
//        for (ArtifactItem artifactItem : artifactItems) {
//            File value = getArtifactFile(artifactItem, repoSystem, repoSession, remoteRepos);
//            toReturn.put(artifactItem.getDestFileName(), value);
//        }
//        return toReturn;
//    }
}
