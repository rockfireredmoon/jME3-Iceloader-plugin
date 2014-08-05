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

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLocator;
import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.ClasspathLocator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.vfs2.FileObject;
import icemoon.iceloader.CachingAssetInfo;

/**
 * Locator that locates assets on the classpath, much like {@link ClasspathLocator},
 * but it will also cache any resources it finds if an {@link AssetCacheLocator} is in
 * use as well. To be honest, this is mostly pointless, the only reason I can think you
 * might need this is if your classpath resources are loaded remotely too. This
 * locator may be removed at a later date.
 */
public class ClasspathCachingLocator implements AssetLocator {

    private static final Logger LOG = Logger.getLogger(ClasspathCachingLocator.class.getName());
    private final ClasspathLocator delegate;

    public ClasspathCachingLocator() {
        delegate = new ClasspathLocator();
    }

    public void setRootPath(String rootPath) {
        delegate.setRootPath(rootPath);
    }

    public AssetInfo locate(AssetManager manager, final AssetKey key) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format("Locating %s in classpath", key.getName()));
        }
        final AssetInfo info = delegate.locate(manager, key);
        if (info != null && AssetCacheLocator.isInUse()) {
            final FileObject cacheRoot = AssetCacheLocator.getVFSRoot();
            if (info != null) {
                return new CachingAssetInfo(manager, key, info, cacheRoot);
            }
        }
        return info;
    }
}
