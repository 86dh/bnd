package test;

import java.io.IOException;

import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.OSInformation;
import aQute.bnd.osgi.Processor;
import aQute.lib.strings.Strings;
import junit.framework.TestCase;

public class ProcessorTest extends TestCase {

	
	
	public void testFixupMerge() throws IOException {
		Processor p = new Processor();
		p.setProperty("-fixupmessages.foo", "foo");
		p.setProperty("-fixupmessages.bar", "bar");
		p.error("foo");
		p.error("bar");
		assertTrue(p.check());
		p.close();
	}

	public void testFixupMacro() throws IOException {
		Processor p = new Processor();
		p.setProperty("skip", "foo");
		p.setProperty("-fixupmessages", "${skip},bar");
		p.error("foo");
		p.error("bar");
		assertTrue(p.check());
		p.close();
	}

	public void testNative() throws IOException {
		Processor p = new Processor();
		
		String s = p._native_capability(new String[]{"_native_capability"});
		System.out.println(s);
		assertNotNull(s);
		
		s = Strings.join(OSInformation.getProcessorAliases("x86-64"));
		assertEquals(s, Strings.join(OSInformation.getProcessorAliases("X86-64")));
		assertEquals(s, Strings.join(OSInformation.getProcessorAliases("x86_64")));
		assertEquals(s, Strings.join(OSInformation.getProcessorAliases("AMD64")));
		assertEquals(s, Strings.join(OSInformation.getProcessorAliases("em64t")));
		
		p.close();
	}
	
	
	public static void testPlugins() {

	}
	
	public void testFixupMessages() throws IOException {
		Processor p = new Processor();
		p.setTrace(true);
		
		p.error("abc");
		assertFalse(p.isOk());

		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;restrict:=warning");
		assertEquals(1,p.getErrors().size());
		assertEquals(0,p.getWarnings().size());
		
		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc");
		assertEquals(0,p.getErrors().size());
		assertEquals(0,p.getWarnings().size());
		
		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;is:=error");
		assertEquals(1,p.getErrors().size());
		assertEquals(0,p.getWarnings().size());
		
		p.clear();
		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;is:=warning");
		assertEquals(0,p.getErrors().size());
		assertEquals(1,p.getWarnings().size());
		
		p.clear();
		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;replace:=def");
		assertEquals("def", p.getErrors().get(0));
		assertEquals(0,p.getWarnings().size());
		
		p.clear();
		p.setProperty(Constants.FIXUPMESSAGES, "'abc def\\s*ghi';is:=warning");
		p.error("abc def  \t\t   ghi");
		assertEquals(0,p.getErrors().size());
		assertEquals(1,p.getWarnings().size());
		
		p.error("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;replace:=def;is:=warning");
		assertEquals("def", p.getWarnings().get(0));
		assertEquals(0,p.getErrors().size());

		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;restrict:=error");
		assertEquals(0,p.getErrors().size());
		assertEquals(1,p.getWarnings().size());
		
		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc");
		assertEquals(0,p.getErrors().size());
		assertEquals(0,p.getWarnings().size());
		
		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;is:=warning");
		assertEquals(0,p.getErrors().size());
		assertEquals(1,p.getWarnings().size());
		
		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;is:=error");
		assertEquals(1,p.getErrors().size());
		assertEquals(0,p.getWarnings().size());
		
		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;replace:=def");
		assertEquals("def", p.getWarnings().get(0));
		assertEquals(0,p.getErrors().size());
		
		p.clear();
		p.warning("abc");
		p.setProperty(Constants.FIXUPMESSAGES, "abc;replace:=def;is:=error");
		assertEquals("def", p.getErrors().get(0));
		assertEquals(0,p.getWarnings().size());
		p.close();
	}

	public static void testDuplicates() {
		assertEquals("", Processor.removeDuplicateMarker("~"));

		assertTrue(Processor.isDuplicate("abc~"));
		assertTrue(Processor.isDuplicate("abc~~~~~~~~~"));
		assertTrue(Processor.isDuplicate("~"));
		assertFalse(Processor.isDuplicate(""));
		assertFalse(Processor.isDuplicate("abc"));
		assertFalse(Processor.isDuplicate("ab~c"));
		assertFalse(Processor.isDuplicate("~abc"));

		assertEquals("abc", Processor.removeDuplicateMarker("abc~"));
		assertEquals("abc", Processor.removeDuplicateMarker("abc~~~~~~~"));
		assertEquals("abc", Processor.removeDuplicateMarker("abc"));
		assertEquals("ab~c", Processor.removeDuplicateMarker("ab~c"));
		assertEquals("~abc", Processor.removeDuplicateMarker("~abc"));
		assertEquals("", Processor.removeDuplicateMarker(""));
		assertEquals("", Processor.removeDuplicateMarker("~~~~~~~~~~~~~~"));
	}

	public static void appendPathTest() throws Exception {
		assertEquals("a/b/c", Processor.appendPath("", "a/b/c/"));
		assertEquals("a/b/c", Processor.appendPath("", "/a/b/c"));
		assertEquals("a/b/c", Processor.appendPath("/", "/a/b/c/"));
		assertEquals("a/b/c", Processor.appendPath("a", "b/c/"));
		assertEquals("a/b/c", Processor.appendPath("a", "b", "c"));
		assertEquals("a/b/c", Processor.appendPath("a", "b", "/c/"));
		assertEquals("a/b/c", Processor.appendPath("/", "a", "b", "/c/"));
		assertEquals("a/b/c", Processor.appendPath("////////", "////a////b///c//"));

	}
}
