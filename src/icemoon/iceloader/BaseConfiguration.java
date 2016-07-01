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
package icemoon.iceloader;

public abstract class BaseConfiguration<B> {

	protected final B backingObject;
	protected String assetPath;

	public BaseConfiguration(String assetPath, B backingObject) {
		this.backingObject = backingObject;
		this.assetPath = assetPath;
	}

	public B getBackingObject() {
		return backingObject;
	}

	public final String getAssetFolder() {
		int dx = assetPath.lastIndexOf('/');
		return dx == -1 ? null : assetPath.substring(0, dx);
	}

	public final String getAssetName() {
		int dx = assetPath.lastIndexOf('/');
		return dx == -1 ? assetPath : assetPath.substring(dx  +1);
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

	public String getAbsoluteAssetPath() {
		return absolutize(getAssetPath());
	}

	public String getAssetPath() {
		return assetPath;
	}
}
