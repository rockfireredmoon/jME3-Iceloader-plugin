/*
 * Copyright (c) 2013-2014 Emeral Icemoon All rights reserved.
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

import icemoon.iceloader.IndexedAssetLocator;
import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.scene.plugins.ogre.MeshLoader;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import icemoon.iceloader.AssetIndex;
import icemoon.iceloader.IndexItem;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

/**
 * Locator that locates assets on the classpath, much like {@link ClasspathLocator},
 * but also provides an index of all classpath resources too.
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

    @Override
    public AssetIndex getIndex(AssetManager assetManager) {
        if (!loadedAssetIndex) {
            MeshLoader ml;
            assetIndex = new AssetIndex(assetManager);
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                    .addUrls(ClasspathHelper.forJavaClassPath())
                    .setScanners(new ResourcesScanner()));
            for (String s : reflections.getResources(Pattern.compile(".*"))) {
                assetIndex.getBackingObject().add(new IndexItem(s));
            }
            loadedAssetIndex = true;
        }
        return assetIndex;
    }
}
