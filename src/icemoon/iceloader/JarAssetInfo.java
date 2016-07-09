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
package icemoon.iceloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.vfs2.FileObject;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.AssetNotFoundException;

public class JarAssetInfo extends LoaderAssetInfo {
	private static final Logger LOG = Logger.getLogger(JarAssetInfo.class.getName());

	private String suffix;
	private AssetInfo delegate;

	public JarAssetInfo(AssetManager manager, AssetKey<?> key, String suffix, AssetInfo delegate) {
		super(manager, key);
		if (delegate == null)
			throw new IllegalArgumentException("Delegate must be supplied.");
		this.suffix = suffix;
		this.delegate = delegate;
	}

	@Override
	public InputStream openStream() {
		if(LOG.isLoggable(Level.FINE))
			LOG.fine(String.format("Opening Jar stream for %s", key.getName()));
		if (delegate instanceof CachingAssetInfo) {
			CachingAssetInfo ca = (CachingAssetInfo) delegate;
			try {
				FileObject cacheRoot = ca.getCacheRoot();
				if (cacheRoot.getURL().getProtocol().equals("file")) {
					try {
						File cacheFile = new File(cacheRoot.getURL().toURI() + "/" + key.getName());
						if (cacheFile.exists()) {
							return extractFromJarFile(cacheFile, false);
						}
					} catch (URISyntaxException e) {
						throw new AssetNotFoundException(
								String.format("Could not locate asset %s in cached archive %s.", suffix, key), e);
					}
				} else {
					FileObject cacheFile = cacheRoot.resolveFile(key.getName());
					if (cacheFile.exists()) {
						cacheFile = cacheRoot.getFileSystem().getFileSystemManager().resolveFile(
								"jar:" + cacheRoot.getName().getURI() + "/" + key.getName() + "!" + suffix);
						return cacheFile.getContent().getInputStream();
					}
				}
			} catch (IOException e) {
				throw new AssetNotFoundException(
						String.format("Could not locate asset %s in cached archive %s.", suffix, key), e);
			}
		}

		// return extractOnTheFly();

		try {
			final InputStream stream = delegate.openStream();
			File tempFile = File.createTempFile("ast", ".tmp");
			tempFile.deleteOnExit();
			try {
				FileOutputStream fos = new FileOutputStream(tempFile);
				try {
					LoaderUtils.copy(stream, fos);
				} finally {
					fos.close();
				}
			} finally {
				stream.close();
			}
			return extractFromJarFile(tempFile, true);

			// final JarInputStream jin = new JarInputStream(stream);
			// JarEntry jen = null;
			// LOG.info(String.format("Scanning stream of %s looking for %s
			// (%s)", delegate.getKey(), key, suffix));
			// final Set<Boolean> holder = new HashSet<Boolean>();
			// while (holder.isEmpty() && (jen = jin.getNextJarEntry()) != null)
			// {
			// LOG.info(String.format(" Found %s", jen.getName()));
			// if (jen.getName().equals(suffix)) {
			// return new InputStream() {
			//
			// @Override
			// public int read(byte[] b) throws IOException {
			// return jin.read(b);
			// }
			//
			// @Override
			// public int read(byte[] b, int off, int len) throws IOException {
			// return jin.read(b, off, len);
			// }
			//
			// @Override
			// public void close() throws IOException {
			// super.close();
			//
			// // Sink the end of the streamm so the entire
			// // file gets cached
			// LOG.info(String.format(" Sinking to end of stream"));
			//
			// while (jin.getNextJarEntry() != null);
			// holder.add(Boolean.TRUE);
			//// byte[] buf = new byte[8192];
			//// while (stream.read(buf) != -1)
			//// ;
			// jin.close();
			// }
			//
			// @Override
			// public int read() throws IOException {
			// return jin.read();
			// }
			// };
			// }
			// }
		} catch (IOException e) {
			throw new AssetNotFoundException(
					String.format("Could not extract asset %s from archive stream %s.", suffix, key), e);
		}
		// throw new AssetNotFoundException(
		// String.format("Could not find asset %s i archive stream %s.", suffix,
		// key));

	}

	private InputStream extractFromJarFile(final File cacheFile, final boolean deleteOnClose) throws IOException {
		if (LOG.isLoggable(Level.FINE))
			LOG.fine(String.format("Extracting %s from %s (which is %d bytes big)", key.getName(), cacheFile,
					cacheFile.length()));
		long now = System.currentTimeMillis();
		final JarFile jf = new JarFile(cacheFile);
		JarEntry jent = jf.getJarEntry(suffix);
		if (jent == null) {
			throw new AssetNotFoundException(
					String.format("Could not find asset %s i archive stream %s.", suffix, key));
		}
		if (LOG.isLoggable(Level.FINE))
			LOG.fine(String.format("%s took %dms to locate in the jar", key.getName(),
					System.currentTimeMillis() - now));
		now = System.currentTimeMillis();
		final InputStream is = jf.getInputStream(jent);
		if (LOG.isLoggable(Level.FINE))
			LOG.fine(String.format("%s took %dms to open the input stream", key.getName(),
					System.currentTimeMillis() - now));
		return new InputStream() {
			@Override
			public int read() throws IOException {
				return is.read();
			}

			@Override
			public int read(byte[] b) throws IOException {
				return is.read(b);
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return is.read(b, off, len);
			}

			@Override
			public void close() throws IOException {
				try {
					is.close();
				} finally {
					jf.close();
				}
				if (deleteOnClose) {
					cacheFile.delete();
				}
			}
		};
	}

	private InputStream extractOnTheFly() {
		try {
			final InputStream stream = delegate.openStream();
			final JarInputStream jin = new JarInputStream(stream);
			JarEntry jen = null;
			if (LOG.isLoggable(Level.FINE))
				LOG.fine(String.format("Scanning stream of %s looking for %s (%s)", delegate.getKey(), key, suffix));
			final Set<Boolean> holder = new HashSet<Boolean>();
			while (holder.isEmpty() && (jen = jin.getNextJarEntry()) != null) {
				if (LOG.isLoggable(Level.FINE))
					LOG.fine(String.format(" Found %s", jen.getName()));
				if (jen.getName().equals(suffix)) {
					return new InputStream() {

						@Override
						public int read(byte[] b) throws IOException {
							return jin.read(b);
						}

						@Override
						public int read(byte[] b, int off, int len) throws IOException {
							return jin.read(b, off, len);
						}

						@Override
						public void close() throws IOException {
							super.close();

							// Sink the end of the streamm so the entire
							// file gets cached
							if (LOG.isLoggable(Level.FINE))
								LOG.fine(String.format(" Sinking to end of stream"));

							while (jin.getNextJarEntry() != null)
								;
							holder.add(Boolean.TRUE);
							// byte[] buf = new byte[8192];
							// while (stream.read(buf) != -1)
							// ;
							jin.close();
						}

						@Override
						public int read() throws IOException {
							return jin.read();
						}
					};
				}
			}
		} catch (IOException e) {
			throw new AssetNotFoundException(
					String.format("Could not extract asset %s from archive stream %s.", suffix, key), e);
		}
		throw new AssetNotFoundException(String.format("Could not find asset %s i archive stream %s.", suffix, key));
	}

	@Override
	public boolean isDecryptedStream() {
		return false;
	}

}