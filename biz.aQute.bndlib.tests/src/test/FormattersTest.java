package test;

import java.util.Arrays;
import java.util.Collection;

import aQute.bnd.build.model.conversions.CollectionFormatter;
import aQute.bnd.build.model.conversions.Converter;
import junit.framework.TestCase;

@SuppressWarnings("restriction")
public class FormattersTest extends TestCase {

	public void testCollectionFormatter() {
		Converter<String,Collection< ? >> formatter = new CollectionFormatter<Object>(",\\\n\t", (String) null);
		String formatted = formatter.convert(Arrays.asList(new String[] {
				"a", "b", "c"
		}));
		assertEquals("\\\n\ta,\\\n\tb,\\\n\tc", formatted);
	}

	/*
	 * Don't add leading separator for single entries
	 */
	public void testCollectionFormatterSingleEntry() {
		Converter<String,Collection< ? >> formatter = new CollectionFormatter<Object>(",\\\n\t", (String) null);
		String formatted = formatter.convert(Arrays.asList(new String[] {
				"a"
		}));
		assertEquals("a", formatted);
	}

}
