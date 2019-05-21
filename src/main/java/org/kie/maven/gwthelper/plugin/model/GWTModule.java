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
package org.kie.maven.gwthelper.plugin.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Class used to represent a <b>gwt.xml</b> file
 */
public class GWTModule {

    private List<String> sourcePaths = new ArrayList<>();
    private List<String> inherits = new ArrayList<>();

    public List<String> getSourcePaths() {
        return sourcePaths;
    }

    public List<String> getInherits() {
        return inherits;
    }

    @Override
    public String toString() {
        return "GWTModule{" +
                "sourcePaths=" + sourcePaths +
                ", inherits=" + inherits +
                '}';
    }
}
