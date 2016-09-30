/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.arquillian.osgi.util;

import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Descriptors;
import aQute.bnd.osgi.Packages;
import aQute.bnd.osgi.Processor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.osgi.framework.Constants;

/**
 * BundleGeneratorHelper
 *
 * @author <a href="mailto:cristina.gonzalez@liferay.com">Cristina González Castellano</a>
 */
public class BundleGeneratorHelper {

    public static void generateManifest(JavaArchive archive, List<Archive<?>> classPath, Properties properties) throws Exception {
        Analyzer analyzer = new Analyzer();

        try {
            List<File> files = new ArrayList<File>();

            File archiveFile = getFileFromArchive(archive);

            files.add(archiveFile);

            analyzer.setJar(archiveFile);

            analyzer.setProperties(properties);

            analyzer.setProperty("Bundle-Version", "1.0.0");

            for (Archive<?> classPathArchive : classPath) {
                File classPathFile = getFileFromArchive(classPathArchive);

                analyzer.addClasspath(classPathFile);

                files.add(archiveFile);
            }

            analyzer.analyze();

            Packages classpathExports = analyzer.getClasspathExports();

            Packages imports = analyzer.getImports();

            Packages importsFiltered = new Packages();

            for (Descriptors.PackageRef importPackage : imports.keySet()) {
                if (!classpathExports.containsKey(importPackage)) {
                    importsFiltered.put(importPackage);
                }
            }

            Manifest manifest = analyzer.calcManifest();

            manifest.getMainAttributes().putValue(Constants.IMPORT_PACKAGE,  Processor.printClauses(importsFiltered, true));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            manifest.write(baos);

            ByteArrayAsset byteArrayAsset = new ByteArrayAsset(baos.toByteArray());

            archive.delete(JarFile.MANIFEST_NAME);

            archive.add(byteArrayAsset, JarFile.MANIFEST_NAME);

            for (File file : files) {
                Files.deleteIfExists(Paths.get(file.toURI()));
            }
        }
        finally {
            analyzer.close();
        }

    }

    protected static File getFileFromArchive(Archive<?> archive) throws Exception {
        File archiveFile = File.createTempFile(archive.getName() + UUID.randomUUID(), ".jar");

        archive.as(ZipExporter.class).exportTo(archiveFile, true);

        return archiveFile;
    }

}
