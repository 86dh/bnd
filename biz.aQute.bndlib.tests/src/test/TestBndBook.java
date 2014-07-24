package test;

import junit.framework.*;
import aQute.bnd.osgi.*;
import aQute.lib.io.*;

@SuppressWarnings("resource")
public class TestBndBook extends TestCase {

	public static void testFilterout() throws Exception {
		Builder b = new Builder();
		b.addClasspath(IO.getFile("jar/osgi.jar"));
		b.addClasspath(IO.getFile("jar/ds.jar"));
		b.setProperty("Export-Package", "org.eclipse.*, org.osgi.*");
		b.setProperty("fwusers", "${classes;importing;org.osgi.framework}");
		b.setProperty("foo", "${filterout;${fwusers};org\\.osgi\\..*}");
		b.build();
		String fwusers = b.getProperty("fwusers");
		String foo = b.getProperty("foo");
		assertTrue(fwusers.length() > foo.length());
		assertTrue(fwusers.indexOf("org.osgi.framework.ServicePermission") >= 0);
		assertTrue(fwusers.indexOf("org.eclipse.equinox.ds.instance.BuildDispose") >= 0);
		assertFalse(foo.indexOf("org.osgi.framework.ServicePermission") >= 0);
		assertTrue(foo.indexOf("org.eclipse.equinox.ds.instance.BuildDispose") >= 0);
		System.err.println(b.getProperty("fwusers"));
		System.err.println(b.getProperty("foo"));

	}
}
