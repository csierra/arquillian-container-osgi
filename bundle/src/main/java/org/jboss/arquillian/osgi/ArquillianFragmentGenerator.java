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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.osgi.util.BundleGeneratorHelper;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.osgi.framework.Constants;

/**
 * ArquillianFragmentGenerator
 *
 * @author <a href="mailto:cristina.gonzalez@liferay.com">Cristina González Castellano</a>
 */
public class ArquillianFragmentGenerator {

    public Archive<?> createArquillianFragment(String symbolicName, String version, TestClass testClass)
        throws Exception {
        JavaArchive arquillianFragmentBundleArchive = ShrinkWrap.create(
            JavaArchive.class, symbolicName + "-fragment.jar");

        arquillianFragmentBundleArchive.addClass(testClass.getJavaClass());

        Properties properties = new Properties();

        properties.setProperty(Constants.BUNDLE_SYMBOLICNAME, symbolicName+"-fragment");
        properties.setProperty(Constants.BUNDLE_NAME, symbolicName + " Fragment");
        properties.setProperty(Constants.BUNDLE_VERSION, "1.0.0");
        properties.setProperty(TEST_BUNDLE_SYMBOLIC_NAME, symbolicName);
        properties.setProperty(TEST_BUNDLE_VERSION, "1.0.0");
        properties.setProperty("Fragment-Host", ArquillianBundleGenerator.BUNDLE_SYMBOLIC_NAME);

        ArquillianBundleArchive arquillianBundleArchive = _arquillianBundleArchiveInstance.get();

        List<Archive<?>> archives = new ArrayList<Archive<?>>();

        archives.addAll(arquillianBundleArchive.getExtensions());
        archives.add(arquillianBundleArchive.getArchive());

        BundleGeneratorHelper.generateManifest(arquillianFragmentBundleArchive, archives, properties);

        return arquillianFragmentBundleArchive;
    }

    @Inject
    private Instance<ArquillianBundleArchive> _arquillianBundleArchiveInstance;


    public static final String TEST_BUNDLE_SYMBOLIC_NAME = "Test-Bundle-Symbolic-Name";

    public static final String TEST_BUNDLE_VERSION = "Test-Bundle-Version";

}
