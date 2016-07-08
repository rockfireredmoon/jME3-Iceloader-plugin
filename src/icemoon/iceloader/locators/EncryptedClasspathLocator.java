/*
 * Copyright (c) 2013-2016 Emerald Icemoon All rights reserved.
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

import javax.crypto.spec.SecretKeySpec;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;

import icemoon.iceloader.DecryptedAssetInfo;
import icemoon.iceloader.EncryptionContext;

/**
 * Extension of {@link ClasspathLocator} than expects assets it finds to be
 * encrypted, and so will decrypt before returning to JME.
 */
public class EncryptedClasspathLocator extends ClasspathLocator {
	private final SecretKeySpec secret;

	public EncryptedClasspathLocator() {
		try {
			secret = EncryptionContext.get().createKey();
		} catch (Exception ex) {
			throw new RuntimeException("Failed to initialize asset manager.", ex);
		}
	}

	@Override
	public AssetInfo locate(AssetManager manager, @SuppressWarnings("rawtypes") AssetKey key) {
		final AssetInfo info = super.locate(manager, key);
		if (info != null && !(info instanceof DecryptedAssetInfo)) {
			return new DecryptedAssetInfo(manager, key, info, secret);

		}
		return info;
	}
}
