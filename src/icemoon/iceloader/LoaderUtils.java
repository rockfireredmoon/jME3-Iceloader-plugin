package icemoon.iceloader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class LoaderUtils {

	public static URL ensureEndsWithSlash(URL url) {
		try {
			return new URL(ensureEndsWithSlash(url.toExternalForm()));
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public static String ensureEndsWithSlash(String path) {
		if (!path.endsWith("/"))
			path += "/";
		return path;
	}

	public static  InputStream dumpStream(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		LoaderUtils.copy(in, baos);
		System.err.println("--BEGIN STREAM DUMP--");
		System.err.println(new String(baos.toByteArray()));
		System.err.println("--END STREAM DUMP--");
		in = new ByteArrayInputStream(baos.toByteArray());
		return in;
	}

	public static void copy(InputStream in, OutputStream out) throws IOException {
		copy(in, out, 8192);
	}

	public static void copy(InputStream in, OutputStream out, int bs) throws IOException {
		byte[] buf = new byte[bs];
		int r;
		while ((r = in.read(buf)) != -1) {
			out.write(buf, 0, r);
		}
		out.flush();
	}
}
