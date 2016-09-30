/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.osgi;

import org.jboss.arquillian.container.spi.event.container.AfterDeploy;
import org.jboss.arquillian.container.spi.event.container.BeforeSetup;
import org.jboss.arquillian.container.spi.event.container.BeforeStop;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.api.annotation.Observes;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.osgi.ArquillianBundleGenerator;
import org.jboss.arquillian.test.spi.event.suite.BeforeClass;
import org.jboss.osgi.metadata.OSGiMetaData;
import org.jboss.osgi.metadata.OSGiMetaDataBuilder;
import org.jboss.shrinkwrap.api.Archive;

import java.util.jar.Manifest;

/**
 * DeploymentObserver
 *
 * @author mbasovni@redhat.com
 */
public class DeploymentObserver {

    public void buildArquillianOSGiBundle(@Observes BeforeSetup event) throws Exception {
        if (_arquillianOSGiBundle == null) {
            ServiceLoader serviceLoader = _serviceLoaderInstance.get();

            ArquillianBundleGenerator arquillianBundleGenerator =
                serviceLoader.onlyOne(ArquillianBundleGenerator.class);

            _arquillianOSGiBundle = arquillianBundleGenerator.createArquillianBundle();
        }
    }

    public void resetContext(@Observes BeforeClass event) throws Exception {
        _resetContext = true;
    }

    public void autostartBundle(@Observes AfterDeploy event) throws Exception {
        if (event.getDeployableContainer() instanceof CommonDeployableContainer) {
            CommonDeployableContainer<?> container = (CommonDeployableContainer<?>) event.getDeployableContainer();

            Manifest manifest = new Manifest(event.getDeployment().getArchive().get("/META-INF/MANIFEST.MF").getAsset().openStream());
            OSGiMetaData metadata = OSGiMetaDataBuilder.load(manifest);

            if (_resetContext) {
                container.uninstallBundle("arquillian-osgi-bundle", "1.0.0");
                container.installBundle(_arquillianOSGiBundle, true);

                //Resolve Fragment
                container.resolveFragments(ArquillianBundleGenerator.BUNDLE_SYMBOLIC_NAME, ArquillianBundleGenerator.BUNDLE_VERSION);
                container.resolveBundle(metadata.getBundleSymbolicName(), metadata.getBundleVersion().toString());

                _resetContext = false;
            }

            if (container.isAutostartBundle()) {
                container.startBundle(metadata.getBundleSymbolicName(), metadata.getBundleVersion().toString());
            }
        }
    }

    public void stopContainer(@Observes BeforeStop event) throws Exception {
        if (event.getDeployableContainer() instanceof CommonDeployableContainer) {
            CommonDeployableContainer<?> container = (CommonDeployableContainer<?>) event.getDeployableContainer();

            container.uninstallBundle("arquillian-osgi-bundle", "1.0.0");
        }
    }

    private boolean _resetContext = false;

    private Archive<?> _arquillianOSGiBundle;

    @Inject
    private Instance<ServiceLoader> _serviceLoaderInstance;

}
