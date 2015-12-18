package test;

import java.util.Arrays;

import aQute.bnd.osgi.Instruction;
import aQute.bnd.osgi.Instructions;
import junit.framework.TestCase;

public class InstructionTest extends TestCase {

	public static void testSelect() {
		assertEquals(Arrays.asList("a", "c"), new Instructions("b").reject(Arrays.asList("a", "b", "c")));
		assertEquals(Arrays.asList("a", "c"), new Instructions("a,c").select(Arrays.asList("a", "b", "c"), false));
		assertEquals(Arrays.asList("a", "c"), new Instructions("!b,*").select(Arrays.asList("a", "b", "c"), false));
	}

	public static void testWildcard() {
		assertTrue(new Instruction("a|b").matches("a"));
		assertTrue(new Instruction("a|b").matches("b"));
		assertTrue(new Instruction("com.foo.*").matches("com.foo"));
		assertTrue(new Instruction("com.foo.*").matches("com.foo.bar"));
		assertTrue(new Instruction("com.foo.*").matches("com.foo.bar.baz"));

		assertTrue(new Instruction("!com.foo.*").matches("com.foo"));
		assertTrue(new Instruction("!com.foo.*").matches("com.foo.bar"));
		assertTrue(new Instruction("!com.foo.*").matches("com.foo.bar.baz"));

		assertTrue(new Instruction("com.foo.*~").matches("com.foo"));
		assertTrue(new Instruction("com.foo.*~").matches("com.foo.bar"));
		assertTrue(new Instruction("com.foo.*~").matches("com.foo.bar.baz"));

		assertTrue(new Instruction("!com.foo.*~").matches("com.foo"));
		assertTrue(new Instruction("!com.foo.*~").matches("com.foo.bar"));
		assertTrue(new Instruction("!com.foo.*~").matches("com.foo.bar.baz"));

		assertTrue(new Instruction("com.foo.*~").isDuplicate());
		assertTrue(new Instruction("!com.foo.*~").isDuplicate());
		assertTrue(new Instruction("!com.foo.*~").isNegated());

	}

	public static void testLiteral() {
		assertTrue(new Instruction("literal").isLiteral());
		assertTrue(new Instruction("literal").matches("literal"));
		assertTrue(new Instruction("!literal").matches("literal"));
		assertTrue(new Instruction("=literal").matches("literal"));
		assertTrue(new Instruction("literal~").matches("literal"));
		assertTrue(new Instruction("!literal~").matches("literal"));
		assertTrue(new Instruction("=literal~").matches("literal"));
		assertFalse(new Instruction("=literal").matches(""));
		assertFalse(new Instruction("!literal").matches(""));
		assertFalse(new Instruction("literal").matches(""));
		assertTrue(new Instruction("literal").isLiteral());
		assertTrue(new Instruction("=literal").isLiteral());
		assertTrue(new Instruction("!literal").isNegated());
		assertTrue(new Instruction("!=literal").isNegated());
		assertTrue(new Instruction("=*********").isLiteral());
	}
}
