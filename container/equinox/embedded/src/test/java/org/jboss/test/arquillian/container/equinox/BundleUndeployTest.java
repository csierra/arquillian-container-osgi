/**
 * Copyright (c) 2000-2013 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package org.jboss.test.arquillian.container.equinox;

import org.jboss.arquillian.container.osgi.EmbeddedContainerConfiguration;
import org.jboss.arquillian.container.osgi.equinox.EquinoxEmbeddedDeployableContainer;
import org.jboss.osgi.metadata.OSGiManifestBuilder;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.test.arquillian.container.equinox.sub.A;
import org.jboss.test.arquillian.container.equinox.sub.B;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.wiring.FrameworkWiring;

import java.io.InputStream;
import java.util.Collections;

/**
 * @author Carlos Sierra Andrés
 */
public class BundleUndeployTest {

    //Class to obtain the framework from the container
    private static class MyEquinoxContainer extends EquinoxEmbeddedDeployableContainer {
        private Framework framework;

        @Override
        protected Framework createFramework(EmbeddedContainerConfiguration conf) {
            framework = super.createFramework(conf);

            return framework;
        }

        public Framework getFramework() {
            return framework;
        }
    }

    @Test
    public void testUninstall() throws Exception {
        MyEquinoxContainer container = new MyEquinoxContainer();

        EmbeddedContainerConfiguration configuration = new EmbeddedContainerConfiguration();

        configuration.validate();

        container.setup(configuration);

        container.start();

        Framework framework = container.getFramework();

        Bundle aBundle = null;
        Bundle bBundle = null;
        Bundle dependentBundle = null;

        try {
            Archive<?> aBundleArchive = createABundle();

            container.deploy(aBundleArchive);

            container.deploy(createDependentBundle());

            aBundle = findBundle(framework, "A-bundle");

            dependentBundle = findBundle(framework, "dependent-bundle");

            aBundle.start();

            dependentBundle.start();

            Class<?> aClass = dependentBundle.loadClass("org.jboss.test.arquillian.container.equinox.sub.A");

            container.undeploy(aBundleArchive);

            container.deploy(createBBundle());

            bBundle = findBundle(framework, "B-bundle");

            bBundle.start();

            Class<?> bClass = dependentBundle.loadClass("org.jboss.test.arquillian.container.equinox.sub.B");
        } catch (Exception e) {
            throw e;
        } finally {
            uninstall(framework, dependentBundle);
            uninstall(framework, bBundle);
            uninstall(framework, aBundle);

            container.stop();
        }

    }

    private void uninstall(Framework framework, Bundle bundle) {
        if (bundle == null) {
            return;
        }
        try {
            bundle.uninstall();
            framework.adapt(FrameworkWiring.class).refreshBundles(Collections.singleton(bundle));
        } catch (Exception e) {
            //Ignored
        }
    }

    private Bundle findBundle(Framework framework, String bundleSymbolicName) {
        Bundle[] bundles = framework.getBundleContext().getBundles();

        for (Bundle bundle : bundles) {
            if (bundle.getSymbolicName().equals(bundleSymbolicName))
                return bundle;
        }

        return null;
    }

    private Archive<?> createDependentBundle() {
        return ShrinkWrap.create(JavaArchive.class).setManifest(
                new Asset() {
                    @Override
                    public InputStream openStream() {
                        return OSGiManifestBuilder.newInstance().
                                addBundleSymbolicName("dependent-bundle").
                                addBundleManifestVersion(2).
                                addImportPackage(A.class.getPackage().getName()).
                                openStream();
                    }
                }

        );
    }

    private Archive<?> createABundle() {
        return ShrinkWrap.create(JavaArchive.class).setManifest(
                new Asset() {
                    @Override
                    public InputStream openStream() {
                        return OSGiManifestBuilder.newInstance().
                                addBundleSymbolicName("A-bundle").
                                addBundleManifestVersion(2).
                                addExportPackage(A.class.getPackage().getName()).
                                openStream();
                    }
                }
        ).addClass(A.class);
    }

    private Archive<?> createBBundle() {
        return ShrinkWrap.create(JavaArchive.class).setManifest(
                new Asset() {
                    @Override
                    public InputStream openStream() {
                        return OSGiManifestBuilder.newInstance().
                                addBundleSymbolicName("B-bundle").
                                addBundleManifestVersion(2).
                                addExportPackage(B.class.getPackage().getName()).
                                openStream();
                    }
                }
        ).addClass(B.class);
    }
}
