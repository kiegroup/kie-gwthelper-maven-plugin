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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Path;

import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.MXParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParser;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kie.maven.gwthelper.plugin.model.GWTModule;

/**
 * Class used to parse <b>gwt.xml</b> file
 */
public class ModuleReader {

    public static GWTModule readGwtModule(Path modulePath, Log log) throws IOException, XmlPullParserException {
        return readGwtModule(modulePath.toFile(), log);
    }

    public static GWTModule readGwtModule(File moduleFile, Log log) throws IOException, XmlPullParserException {
        InputStream inputStream = new FileInputStream(moduleFile);
        return readGwtModule(inputStream, log);
    }

    public static GWTModule readGwtModule(InputStream in, Log log) throws IOException, XmlPullParserException {
        return readGwtModule(ReaderFactory.newXmlReader(in), log);
    }

    private static GWTModule readGwtModule(Reader reader, Log log) throws XmlPullParserException, IOException {
        XmlPullParser xpp = /*addDefaultEntities ? new MXParser(EntityReplacementMap.defaultEntityReplacementMap) : */new MXParser();
        xpp.setInput(reader);
        return readGwtModule(xpp, log);
    }

    private static GWTModule readGwtModule(XmlPullParser xpp, Log log) throws XmlPullParserException, IOException {
        GWTModule toReturn = new GWTModule();
        int eventType = xpp.getEventType();
        while (eventType != xpp.END_DOCUMENT) {
            if (eventType == xpp.START_DOCUMENT) {
                log.debug("Start document");
            } else if (eventType == xpp.START_TAG) {
                log.debug("Start tag " + xpp.getName());
                readAttribute(xpp, toReturn, log);
            } else if (eventType == xpp.END_TAG) {
                log.debug("End tag " + xpp.getName());
            } else if (eventType == xpp.TEXT) {
                log.debug("Text " + xpp.getText());
            } else {
                log.debug("Event type " + xpp.getEventType());
            }
            eventType = xpp.next();
        }
        return toReturn;
    }

    private static void readAttribute(XmlPullParser xpp, GWTModule toPopulate, Log log) {
        log.debug("Start tag " + xpp.getName());
        switch (xpp.getName()) {
            case "inherits":
                readInherits(xpp, toPopulate, log);
                break;
            case "source":
                readSource(xpp, toPopulate, log);
                break;
            default:
                //
        }
    }

    private static void readInherits(XmlPullParser xpp, GWTModule toPopulate, Log log) {
        log.debug("readInherits " + xpp.getName());
        for (int i = 0; i < xpp.getAttributeCount(); i ++) {
            String attributeName = xpp.getAttributeName(i);
            if("name".equals(attributeName)) {
                toPopulate.getInherits().add(xpp.getAttributeValue(i));
            }
        }
    }

    private static void readSource(XmlPullParser xpp, GWTModule toPopulate, Log log) {
        log.debug("readSource " + xpp.getName());
        for (int i = 0; i < xpp.getAttributeCount(); i ++) {
            String attributeName = xpp.getAttributeName(i);
            if("path".equals(attributeName)) {
                toPopulate.getSourcePaths().add(xpp.getAttributeValue(i));
            }
        }
    }
}
