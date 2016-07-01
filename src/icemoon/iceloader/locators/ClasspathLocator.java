/*
 * Copyright (c) 2013-2014 Emerald Icemoon All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *  *
 * * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'jMonkeyEngine' nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package icemoon.iceloader.locators;

import icemoon.iceloader.AssetIndex;
import icemoon.iceloader.ExtendedAssetInfo;
import icemoon.iceloader.IndexedAssetLocator;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;

/**
 * Locator that locates assets on the classpath, much like {@link ClasspathLocator}, but
 * also provides an index of all classpath resources too.
 */
public class ClasspathLocator extends com.jme3.asset.plugins.ClasspathLocator implements IndexedAssetLocator {

    private boolean loadedAssetIndex;
    private static final Logger LOG = Logger.getLogger(ClasspathLocator.class.getName());
    private AssetIndex assetIndex;
    private String rootPath = "";

    public ClasspathLocator() {
    }

    @Override
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
        super.setRootPath(rootPath);
    }

    @Override
    public AssetInfo locate(AssetManager manager, AssetKey key) {
        return super.locate(manager, key);
    }

    public AssetIndex getIndex(AssetManager assetManager) {
        if (!loadedAssetIndex) {
            assetIndex = new AssetIndex(assetManager);
            try {
                for (Enumeration<URL> en = getClass().getClassLoader().getResources(AssetIndex.DEFAULT_RESOURCE_NAME); en.hasMoreElements();) {
                    URL u = en.nextElement();
                    LOG.info("Indexing " + u);
                    assetIndex.load(u.openStream());
					assetIndex.configure(0,
							getClass().getSimpleName().toLowerCase() + "://" + AssetIndex.DEFAULT_RESOURCE_NAME);
                }
            } catch (IOException ioe) {
                LOG.log(Level.WARNING, String.format("Could not index classpath assets.", ioe));
            }

//            final Set<URL> jars = ClasspathHelper.forClassLoader(getClass().getClassLoader().);
//            if (LOG.isLoggable(Level.FINE)) {
//                LOG.fine(String.format("Indexing from jars %s", jars));
//            }
//            Reflections reflections = new Reflections(new ConfigurationBuilder()
//                    .addUrls(jars)
//                    .setScanners(new ResourcesScanner()));
//            for (String s : reflections.getResources(Pattern.compile(".*"))) {
//                if (LOG.isLoggable(Level.FINE)) {
//                    LOG.fine(String.format("    %s", s));
//                }
//                assetIndex.getBackingObject().add(new IndexItem(s));
//            }
            loadedAssetIndex = true;
        }
        return assetIndex;
    }
}
