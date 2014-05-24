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
package icemoon.iceloader;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractConfiguration<B> {

    private final static Logger LOG = Logger.getLogger(AbstractConfiguration.class.getName());
    protected String resourceName;
    protected final B backingObject;
    protected final AssetManager assetManager;

    /**
     * Sub-classes may need to set instance variables before loading, they should use this
     * default constructor.
     *
     * @param backingObject backing object
     * @param assetManager asset manager
     */
    public AbstractConfiguration(B backingObject, AssetManager assetManager) {
        this.backingObject = backingObject;
        this.assetManager = assetManager;
    }

    /**
     * Load template default from the provided classloader.
     *
     * @param resourceName name of terrain configuration resource
     * @param classLoader class loader
     */
    public AbstractConfiguration(String resourceName, AssetManager assetManager, B backingObject) {
        this.resourceName = resourceName;
        this.assetManager = assetManager;
        this.backingObject = backingObject;
        loadResource();
    }

    public B getBackingObject() {
        return backingObject;
    }

    protected abstract void load(InputStream in, B backingObject) throws IOException;

    public String getAssetFolder() {
        int dx = resourceName.lastIndexOf('/');
        return dx == -1 ? null : resourceName.substring(0, dx);
    }

    public String relativize(String resource) {
        if (resource == null) {
            return null;
        }
        String af = getAssetFolder();
        if (resource.startsWith(af)) {
            return resource.substring(af.length() + 1);
        }
        return resource;
    }

    public String absolutize(String name) {
        if (name == null) {
            return null;
        }
        return getAssetFolder() + "/" + name;
    }

    public String getResourceName() {
        return resourceName;
    }

    protected final void loadResource() {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine(String.format("Loading %s", resourceName));
        }

        AssetInfo info = assetManager.locateAsset(new AssetKey<Object>(resourceName));
        if (info == null) {
            throw new AssetNotFoundException("Could not configuration resource " + resourceName);
        }

        try {
            InputStream in = info.openStream();
            if (in == null) {
                throw new AssetNotFoundException("Could not configuration resource " + resourceName);
            }
            try {
                load(in, backingObject);
            } finally {
                in.close();
            }
        } catch (IOException ioe) {
            throw new AssetLoadException("Failed to load asset.", ioe);
        }
    }
}
