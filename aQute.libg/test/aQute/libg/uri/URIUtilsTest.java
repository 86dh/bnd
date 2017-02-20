package aQute.libg.uri;

import java.io.File;
import java.net.URI;

import junit.framework.TestCase;

public class URIUtilsTest extends TestCase {

	public void testResolveAbsolute() throws Exception {
		// reference is absolute, so base is irrelevant
		URI result = URIUtil.resolve(URI.create("http://example.com/foo.xml"), "http://example.org/bar.xml");
		assertEquals("http://example.org/bar.xml", result.toString());
	}

	public void testResolveRelativeHttp() throws Exception {
		URI result = URIUtil.resolve(URI.create("http://example.com/foo.xml"), "bar.xml");
		assertEquals("http://example.com/bar.xml", result.toString());
	}

	public void testResolveRelativeBlank() throws Exception {
		URI result = URIUtil.resolve(URI.create("http://example.com/foo.xml"), "");
		assertEquals("http://example.com/foo.xml", result.toString());
	}

	public void testResolveAbsoluteWindowsPath() throws Exception {
		if (isWindows()) {
			URI result = URIUtil.resolve(URI.create("file:/C:/Users/jimbob/base.txt"), "C:\\Users\\jim\\foo.txt");
			assertEquals("file:/C:/Users/jim/foo.txt", result.toString());
		}
	}

	public void testResolveRelativeWindowsPath() throws Exception {
		if (isWindows()) {
			URI result = URIUtil.resolve(URI.create("file:/C:/Users/jim/base.txt"), "subdir\\foo.txt");
			assertEquals("/C:/Users/jim/subdir/foo.txt", result.getPath());
		}
	}

	private static boolean isWindows() {
		return File.separatorChar == '\\';
	}

}
