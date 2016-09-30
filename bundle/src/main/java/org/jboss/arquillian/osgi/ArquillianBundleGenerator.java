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
package org.jboss.arquillian.osgi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.jboss.arquillian.container.test.spi.RemoteLoadableExtension;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.InstanceProducer;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.osgi.util.BundleGeneratorHelper;
import org.jboss.arquillian.protocol.jmx.JMXTestRunner;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.Filters;
import org.jboss.shrinkwrap.api.Node;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.osgi.framework.BundleException;

import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ArquillianBundleGenerator
 *
 * @author <a href="mailto:cristina.gonzalez@liferay.com">Cristina González Castellano</a>
 */
public class ArquillianBundleGenerator {

    public Archive<?> createArquillianBundle()
        throws Exception{

        JavaArchive arquillianOSGiBundleArchive = ShrinkWrap.create(
            JavaArchive.class, "arquillian-osgi-bundle.jar");

        arquillianOSGiBundleArchive.addClass(ArquillianBundleActivator.class);

        arquillianOSGiBundleArchive.addPackage(JMXTestRunner.class.getPackage());

        Properties properties = new Properties();

        properties.setProperty(Constants.BUNDLE_SYMBOLICNAME, BUNDLE_SYMBOLIC_NAME);
        properties.setProperty(Constants.BUNDLE_NAME, BUNDLE_NAME);
        properties.setProperty(Constants.BUNDLE_VERSION, BUNDLE_VERSION);
        properties.setProperty(Constants.BUNDLE_ACTIVATOR, ArquillianBundleActivator.class.getCanonicalName());
        properties.setProperty(Constants.IMPORT_PACKAGE, "*,org.osgi.framework.startlevel,javax.naming");

        properties.setProperty(Constants.EXPORT_PACKAGE, OSGiManifestBuilder.class.getPackage().getName());

        List<Archive<?>> extensionArchives = loadAuxiliaryArchives();

        BundleGeneratorHelper.generateManifest(
            arquillianOSGiBundleArchive, extensionArchives, properties);

        handleAuxiliaryArchives(arquillianOSGiBundleArchive, extensionArchives);

        ArquillianBundleArchive arquillianBundleArchive = new ArquillianBundleArchive();

        arquillianBundleArchive.setArchive(arquillianOSGiBundleArchive);

        arquillianBundleArchive.setExtensions(extensionArchives);

        _arquillianBundleArchiveInstanceProducer.set(arquillianBundleArchive);

        return arquillianOSGiBundleArchive;
    }

    public void replaceManifest(Archive archive, Manifest manifest)
        throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        manifest.write(baos);

        ByteArrayAsset byteArrayAsset = new ByteArrayAsset(baos.toByteArray());

        archive.delete(JarFile.MANIFEST_NAME);

        archive.add(byteArrayAsset, JarFile.MANIFEST_NAME);
    }

    public Manifest getManifest(JavaArchive javaArchive) throws IOException {
        Node manifestNode = javaArchive.get(JarFile.MANIFEST_NAME);

        if (manifestNode == null) {
            return null;
        }

        Asset manifestAsset = manifestNode.getAsset();

        return new Manifest(manifestAsset.openStream());
    }

    public Manifest putAttributeValue(
        Manifest manifest, String attributeName, String... attributeValue)
        throws IOException {

        Attributes mainAttributes = manifest.getMainAttributes();

        String attributeValues = mainAttributes.getValue(attributeName);

        Set<String> attributeValueSet = new HashSet<String>();

        if (attributeValues != null) {
            attributeValueSet.addAll(Arrays.asList(attributeValues.split(",")));
        }

        attributeValueSet.addAll(Arrays.asList(attributeValue));

        StringBuilder sb = new StringBuilder();

        for (String value : attributeValueSet) {
            sb.append(value);
            sb.append(",");
        }

        if (!attributeValueSet.isEmpty()) {
            sb.setLength(sb.length() - 1);
        }

        attributeValues = sb.toString();

        mainAttributes.putValue(attributeName, attributeValues);

        return manifest;
    }

    private void handleAuxiliaryArchives(
        JavaArchive javaArchive, Collection<Archive<?>> auxiliaryArchives)
        throws IOException {

        for (Archive auxiliaryArchive : auxiliaryArchives) {
            Map<ArchivePath, Node> remoteLoadableExtensionMap =
                auxiliaryArchive.getContent(
                    Filters.include(_REMOTE_LOADABLE_EXTENSION_FILE));

            Collection<Node> remoteLoadableExtensions =
                remoteLoadableExtensionMap.values();

            if (remoteLoadableExtensions.size() > 1) {
                throw new RuntimeException(
                    "The archive " + auxiliaryArchive.getName() +
                        " contains more than one RemoteLoadableExtension file");
            }

            if (remoteLoadableExtensions.size() == 1) {
                Iterator<Node> remoteLoadableExtensionsIterator =
                    remoteLoadableExtensions.iterator();

                Node remoteLoadableExtensionsNext =
                    remoteLoadableExtensionsIterator.next();

                javaArchive.add(
                    remoteLoadableExtensionsNext.getAsset(),
                    _REMOTE_LOADABLE_EXTENSION_FILE);
            }

            InputStream auxiliaryArchiveInputStream = auxiliaryArchive.as(ZipExporter.class).exportAsInputStream();

            ByteArrayAsset byteArrayAsset = new ByteArrayAsset(auxiliaryArchiveInputStream);

            String path = "extension/" + auxiliaryArchive.getName();

            javaArchive.addAsResource(byteArrayAsset, path);

            Manifest manifest = putAttributeValue(getManifest(javaArchive), "Bundle-ClassPath", ".", path);

            replaceManifest(javaArchive, manifest);

            try {
                validateBundleArchive(auxiliaryArchive);

                Manifest auxiliaryArchiveManifest = getManifest((JavaArchive)auxiliaryArchive);

                Attributes mainAttributes = auxiliaryArchiveManifest.getMainAttributes();

                String value = mainAttributes.getValue("Import-package");

                if (value != null) {
                    String[] importValues = value.split(",");

                    manifest = putAttributeValue(manifest, "Import-Package", importValues);

                    replaceManifest(javaArchive, manifest);
                }
            }
            catch (BundleException be) {
                if (_logger.isInfoEnabled()) {
                    _logger.info("Not processing manifest from " + auxiliaryArchive + ": " + be.getMessage());
                }
            }
        }
    }

    private void validateBundleArchive(Archive<?> archive)
        throws BundleException, IOException {

        Manifest manifest = null;

        Node node = archive.get(JarFile.MANIFEST_NAME);

        if (node != null) {
            manifest = new Manifest(node.getAsset().openStream());
        }

        if (manifest != null) {
            OSGiManifestBuilder.validateBundleManifest(manifest);
        } else {
            throw new BundleException("can't obtain Manifest");
        }
    }

    private List<Archive<?>> loadAuxiliaryArchives() {
        List<Archive<?>> archives = new ArrayList<Archive<?>>();

        // load based on the Containers ClassLoader
        ServiceLoader serviceLoader = _serviceLoaderInstance.get();

        Collection<AuxiliaryArchiveAppender> archiveAppenders = serviceLoader.all(AuxiliaryArchiveAppender.class);

        for (AuxiliaryArchiveAppender archiveAppender : archiveAppenders) {
            Archive<?> auxiliaryArchive = archiveAppender.createAuxiliaryArchive();

            if (auxiliaryArchive != null) {
                archives.add(auxiliaryArchive);
            }
        }

        return archives;
    }

    @Inject
    private Instance<ServiceLoader> _serviceLoaderInstance;

    @Inject
    @ApplicationScoped
    private InstanceProducer<ArquillianBundleArchive> _arquillianBundleArchiveInstanceProducer;

    private static final String _REMOTE_LOADABLE_EXTENSION_FILE = "/META-INF/services/" + RemoteLoadableExtension.class.getCanonicalName();

    private static final Logger _logger = LoggerFactory.getLogger(ApplicationArchiveProcessor.class);


    public static final String BUNDLE_SYMBOLIC_NAME = "arquillian-osgi-bundle";
    public static final String BUNDLE_NAME = "Arquillian Bundle";
    public static final String BUNDLE_VERSION = "1.0.0";

}
