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

import com.jme3.asset.AssetManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class AssetIndex extends AbstractConfiguration<List<IndexItem>> {

    public final static String DEFAULT_RESOURCE_NAME = "index.dat";

    public AssetIndex(AssetManager mgr) {
        super(new ArrayList<IndexItem>(), mgr);
    }

    public AssetIndex(String resourceName, AssetManager assetManager) {
        super(resourceName, assetManager, new ArrayList<IndexItem>());
    }
    
    public void load(InputStream in) throws IOException {
        load(in, getBackingObject());
    }

    @Override
    protected void load(InputStream in, List<IndexItem> backingObject) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            int idx = line.indexOf('\t');
            int idx2 = line.indexOf('\t', idx + 1);
            backingObject.add(new IndexItem(line.substring(0, idx), Long.parseLong(line.substring(idx + 1, idx2)), Long.parseLong(line.substring(idx2 + 1))));
        }
    }
    
    public boolean hasAsset(String name) {
        for(IndexItem i : getBackingObject()) {
            if(i.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public Collection<String> getAssetNamesMatching(Pattern p) {
        List<String> l = new ArrayList<String>();
        for (IndexItem s : getBackingObject()) {
            if (p.matcher(s.getName()).matches()) {
                l.add(s.getName());
            }
        }
        return l;
    }
    
    public IndexItem getAsset(String name) {
        for (IndexItem s : getBackingObject()) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }

    public Collection<? extends IndexItem> getAssetsMatching(Pattern p) {
        List<IndexItem> l = new ArrayList<IndexItem>();
        for (IndexItem s : getBackingObject()) {
            if (p.matcher(s.getName()).matches()) {
                l.add(s);
            }
        }
        return l;
    }
}
