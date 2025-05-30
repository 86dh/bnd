package test;

import static aQute.bnd.osgi.Constants.RESOLUTION_DIRECTIVE;
import static aQute.bnd.test.BndTestCase.assertOk;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.osgi.framework.namespace.ExecutionEnvironmentNamespace.CAPABILITY_VERSION_ATTRIBUTE;
import static org.osgi.framework.namespace.ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import aQute.bnd.build.model.EE;
import aQute.bnd.header.Attrs;
import aQute.bnd.header.OSGiHeader;
import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Descriptors.PackageRef;
import aQute.bnd.osgi.Domain;
import aQute.bnd.osgi.EmbeddedResource;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Packages;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Resource;
import aQute.bnd.osgi.Verifier;
import aQute.bnd.osgi.resource.FilterBuilder;
import aQute.bnd.osgi.resource.RequirementBuilder;
import aQute.bnd.test.jupiter.InjectTemporaryDirectory;
import aQute.bnd.version.Version;
import aQute.bnd.version.VersionRange;
import aQute.lib.collections.SortedList;
import aQute.lib.hex.Hex;
import aQute.lib.io.FileTree;
import aQute.lib.io.IO;
import aQute.lib.strings.Strings;
import aQute.lib.zip.ZipUtil;
import aQute.service.reporter.Report.Location;

@SuppressWarnings("resource")
@ExtendWith(SoftAssertionsExtension.class)
public class BuilderTest {

	@InjectSoftAssertions
	SoftAssertions softly;

	/**
	 * Test version with space
	 *
	 * @throws Exception
	 */

	@Test
	public void testVersionWithSpace() throws Exception {
		try (Builder outer = new Builder()) {
			try (Builder inner = new Builder()) {
				inner.addClasspath(IO.getFile("jar/osgi.core-4.3.0.jar"));
				inner.setProperty("Export-Package", "org.osgi.framework;version=\"1.6.0 \"");
				inner.setProperty("-includepackage", "org.osgi.framework");
				Jar jar = inner.build();
				assertThat(inner.check()).isTrue();

				outer.addClasspath(jar);
				outer.addClasspath(IO.getFile("jar/osgi.core-4.3.0.jar"));
				outer.setProperty("-includepackage", "org.osgi.service.packageadmin");
				outer.build();
				assertThat(outer.check()).isTrue();
			}
		}
	}

	/**
	 * [builder] Access to information generated during doExpand() #5130
	 *
	 * @throws Exception
	 */

	@Test
	public void testSourceInformationRecording() throws Exception {
		try (Builder builder = new Builder()) {
			builder.addClasspath(IO.getFile("jar/osgi.core-4.3.0.jar"));
			builder.setProperty("Export-Package", "org.osgi.framework");
			builder.build();
			assertThat(builder.check());

			PackageRef framework = builder.getPackageRef("org.osgi.framework");
			Attrs attrs = builder.getContained()
				.get(framework);
			assertThat(attrs.get(Constants.INTERNAL_SOURCE_DIRECTIVE)).isEqualTo("osgi.core-4.3.0.201102171602");
		}
	}
	/**
	 * -includepackage: *;from:=classes will generate an error if there are no
	 * classes.
	 */

	@Test
	public void testIncludePackageZeroMatchesForWildcard() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty("-includepackage", "*;from:=osgi");
			Jar build = b.build();
			assertFalse(b.check("The JAR is empty: "));
		}
		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty("-includepackage", "*;from:=nonexistent");
			Jar build = b.build();
			assertTrue(b.check("The JAR is empty: "));
		}
	}

	/**
	 * Check if
	 */

	@Test
	public void testIncludeWholeProject() throws Exception {
		try (Builder b = new Builder()) {
			Jar jar = new Jar(IO.getFile("jar/osgi.jar"));
			jar.putResource(Constants.PROJECT_MARKER, new EmbeddedResource("", 0L));
			b.addClasspath(jar);
			b.setProperty("-includepackage", Constants.ALL_FROM_PROJECT);
			Jar build = b.build();
			assertThat(build.getResource("org/osgi/service/log/LogService.class")).isNotNull();
			assertThat(build.getResource("org/osgi/service/event/EventHandler.class")).isNotNull();
		}
	}

	/**
	 * There shouldn't be "Duplicate name..." warnings with pedantic flag set to
	 * true #2803 https://github.com/bndtools/bnd/issues/2803
	 *
	 * @throws Exception
	 */

	@Test
	public void testNoDuplicateWarningForHeadersThatAllowDuplicates() throws Exception {
		try (Builder b = new Builder()) {
			b.setPedantic(true);
			b.addClasspath(IO.getFile("bin_test"));
			b.setProperty("Export-Package", "a;version=1,a;version=2");
			b.setProperty("Require-Capability", "ns;filter:='(foo=1)',ns;filter:='(foo=2)',ns;filter:='(foo=1)'");
			b.setProperty("Provide-Capability", "ns;foo=1,ns;foo=2");
			Jar build = b.build();
			assertTrue(b.check());
		}
	}

	/**
	 * the opposite of
	 * {@link #testNoDuplicateWarningForHeadersThatAllowDuplicates()} where we
	 * want the duplicate warning.
	 */
	@Test
	public void testDuplicateWarningForHeadersThatDoNotAllowDuplicates() throws Exception {
		try (Builder b = new Builder()) {
			b.setPedantic(true);
			b.addClasspath(IO.getFile("bin_test"));
			b.setProperty("Import-Package", "a;version=1,a;version=2");
			Jar build = b.build();
			softly.assertThat(b.check())
				.isFalse();
			softly.assertThat(b.getWarnings())
				.contains(
					"Duplicate name a used in header: 'a;version=1,a;version=2'. Duplicate names are specially marked in Bnd with a ~ at the end (which is stripped at printing time).");

		}
	}

	/**
	 * Duplicate Export-Package clause exports second and later package with
	 * bundle version #2864 https://github.com/bndtools/bnd/issues/2864
	 */

	@Test
	public void testDuplicateExportPackageClauseExportsSecondAndLaterPackageWithBundleVersion() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("bin_test"));
			b.setBundleVersion("20");
			b.setProperty("Export-Package", "a;version=1,a");
			Jar build = b.build();
			assertTrue(b.check("Export-Package duplicate package name \\(a\\) that uses the default version"));
		}
	}

	/**
	 * Check default version if no version is set
	 */

	@Test
	public void testExportVersionIfNoVersionIsSet() throws Exception {
		try (Builder b = new Builder()) {
			b.setBundleVersion("1000");
			b.addClasspath(IO.getFile("bin_test"));
			b.setProperty("-nodefaultversion", "true");
			b.setProperty("Export-Package", "a");
			Jar build = b.build();
			assertTrue(b.check());
			String value = build.getManifest()
				.getMainAttributes()
				.getValue("Export-Package");
			Parameters ps = new Parameters(value);
			Attrs attrs = ps.get("a");
			String version = attrs.get(Constants.VERSION_ATTRIBUTE);
			assertThat(version).isNull();
		}
	}

	/**
	 * Test the detection of usage of old components: Invalid Service-Component
	 * header
	 */

	@Test
	public void testDetectWrongServiceComponentHeaderWithoutAnnotatedComponents() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("bin_test"));
			b.setProperty("Service-Component", "*");
			b.setProperty("-includeresource", "foo;literal=x");
			Jar build = b.build();
			assertTrue(b.check("Service-Component is normally generated by bnd."));
		}
	}

	/**
	 * Test if the compression flag is set properly
	 */

	@Test
	public void testCompressionSet(@InjectTemporaryDirectory
	File tmp) throws Exception {
		try (Builder b = new Builder()) {
			b.setIncludeResource("foo;literal='x'");
			Jar build = b.build();
			assertThat(build.hasCompression()).isEqualTo(Jar.Compression.DEFLATE);
			File out = new File(tmp, "default.jar");
			build.write(out);
			try (JarFile jarFile = new JarFile(out)) {
				JarEntry entry = jarFile.getJarEntry("foo");
				assertThat(entry.getMethod()).isEqualTo(ZipOutputStream.DEFLATED);
				assertThat(entry.getCrc()).isEqualTo(2363233923L);
			}
		}

		try (Builder b = new Builder()) {
			b.setIncludeResource("foo;literal='x'");
			b.setProperty(Constants.COMPRESSION, "STORE");
			Jar build = b.build();
			assertThat(build.hasCompression()).isEqualTo(Jar.Compression.STORE);
			File out = new File(tmp, "store.jar");
			build.write(out);
			try (JarFile jarFile = new JarFile(out)) {
				JarEntry entry = jarFile.getJarEntry("foo");
				assertThat(entry.getMethod()).isEqualTo(ZipOutputStream.STORED);
				assertThat(entry.getCrc()).isEqualTo(2363233923L);
				assertThat(entry.getCompressedSize()).isEqualTo(entry.getSize());
			}
		}

		try (Builder b = new Builder()) {
			b.setIncludeResource("foo;literal='x'");
			b.setProperty(Constants.COMPRESSION, "DEFLATE");
			Jar build = b.build();
			assertThat(build.hasCompression()).isEqualTo(Jar.Compression.DEFLATE);
			File out = new File(tmp, "deflate.jar");
			build.write(out);
			try (JarFile jarFile = new JarFile(out)) {
				JarEntry entry = jarFile.getJarEntry("foo");
				assertThat(entry.getMethod()).isEqualTo(ZipOutputStream.DEFLATED);
				assertThat(entry.getCrc()).isEqualTo(2363233923L);
			}
		}
	}

	/**
	 * In the bnd file for bndlib, we include DS annotations 1.3 and osgi.cmpn 5
	 * (which includes DS annotations 1.2) on the -buildpath. We use a
	 * -split-package directive to select the first package (DS annotations 1.3)
	 * for the bundle. However during -sources: true processing, bnd ignores the
	 * -split-package directive and includes the sources from all the -buildpath
	 * entries. The means that the DS annotations 1.2 source, coming later in
	 * the -buildpath, overlays the DS annotations 1.3 source. The result is a
	 * mish-mash of DS annotations 1.2 and 1.3 source in OSGI-OPT/src.
	 */
	@Test
	public void testSplitSourcesFirst() throws Exception {

		Builder bmaker = new Builder();
		try {
			bmaker.addClasspath(new File("bin_test"));
			bmaker.addClasspath(new File("jar/osgi.jar"));
			bmaker.addClasspath(new File("jar/osgi-3.0.0.jar"));
			bmaker.setSourcepath(new File[] {
				new File("test")
			});
			bmaker.setProperty("-sources", "true");
			bmaker.setProperty("Export-Package", "org.osgi.framework;-split-package:=first, a");
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			assertNotNull(jar.getResource("OSGI-OPT/src/a/A.java"));
			assertNotNull(jar.getResource("OSGI-OPT/src/a/B.java"));

			InputStream in = jar.getResource("OSGI-OPT/src/org/osgi/framework/Bundle.java")
				.openInputStream();
			assertNotNull(in);
			byte[] fw = IO.read(in);
			assertEquals(39173, fw.length);
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testSplitSourcesMergeLast() throws Exception {

		Builder bmaker = new Builder();
		try {
			bmaker.addClasspath(new File("bin_test"));
			bmaker.addClasspath(new File("jar/osgi.jar"));
			bmaker.addClasspath(new File("jar/osgi-3.0.0.jar"));
			bmaker.setSourcepath(new File[] {
				new File("test")
			});
			bmaker.setProperty("-sources", "true");
			bmaker.setProperty("Export-Package", "org.osgi.framework;-split-package:=merge-last, a");
			Jar jar = bmaker.build();

			assertNotNull(jar.getResource("OSGI-OPT/src/a/A.java"));
			assertNotNull(jar.getResource("OSGI-OPT/src/a/B.java"));

			assertTrue(bmaker.check("Version for package org.osgi.framework is set to different values"));
			InputStream in = jar.getResource("OSGI-OPT/src/org/osgi/framework/Bundle.java")
				.openInputStream();
			assertNotNull(in);
			byte[] fw = IO.read(in);
			assertEquals(25783, fw.length);
		} finally {
			bmaker.close();
		}

	}

	/**
	 * #1017 Wrong import version range being generated On one project I depend
	 * on bundle A and bundle B. Both only export packages in version 1.0. No
	 * package is exported by both of them. But B also embeds one package from A
	 * in version 2.0 (via
	 * http://njbartlett.name/2014/05/26/static-linking.html) but that one
	 * package is not exported from B. Now if the classpath first contains B
	 * then A the import-package statement being generated for that package
	 * (being embedded in B in version 2.0 and exported from A in 1.0) has a
	 * version range starting with 2.0 (which is wrong). Only if the classpath
	 * contains A first and then B, it takes the right import version range for
	 * that package (namely starting with 1.0) I use the maven-bundle-plugin
	 * 2.5.3 with bndlib 2.4.0.
	 */

	@Test
	public void test1017UsingPrivatePackagesVersion() throws Exception {
		Builder A = new Builder();
		A.addClasspath(new File("jar/osgi.jar"));
		A.setExportPackage("org.osgi.service.event");
		A.build();
		assertTrue(A.check());

		Builder B = new Builder();
		B.addClasspath(new File("jar/osgi.jar"));
		B.setExportPackage("org.osgi.service.wireadmin");
		B.setPrivatePackage("org.osgi.service.event");
		B.setIncludeResource("org/osgi/service/event/packageinfo;literal='version 2.0.0'");
		B.setProperty("-fixupmessages.duplicates",
			"includeresource.duplicates");
		B.build();
		assertTrue(B.check());

		Builder B_A = new Builder();
		B_A.addClasspath(B.getJar());
		B_A.addClasspath(A.getJar());
		B_A.addClasspath(new File("bin_test"));

		B_A.setPrivatePackage("test.reference_to_eventadmin");
		B_A.build();
		assertTrue(B_A.check());

		assertEquals("[1.0,2)", B_A.getImports()
			.getByFQN("org.osgi.service.event")
			.getVersion());
		Builder A_B = new Builder();
		A_B.addClasspath(A.getJar());
		A_B.addClasspath(B.getJar());
		A_B.addClasspath(new File("bin_test"));

		A_B.setPrivatePackage("test.reference_to_eventadmin");
		A_B.build();
		assertTrue(A_B.check());

		assertEquals("[1.0,2)", A_B.getImports()
			.getByFQN("org.osgi.service.event")
			.getVersion());

	}

	/**
	 * #971 bnd does not import exported package used by an imported/exported
	 * package When building org.osgi.impl.service.async bundle in OSGi build,
	 * the bundle exports the org.osgi.util.promise and org.osgi.util.function
	 * packages. org.osgi.util.promise uses org.osgi.util.function. bnd
	 * correctly exports both packages but only imports org.osgi.util.promise.
	 *
	 * <pre>
	 *  Export-Package:
	 * org.osgi.service.async;version="1.0";uses:="org.osgi.framework,org.osgi.
	 * util.promise",org.osgi.service.async.delegate;version="1.0";uses:="org.
	 * osgi.util.promise",org.osgi.util.promise;version="1.0";uses:="org.osgi.
	 * util.function",org.osgi.util.function;version="1.0" Import-Package:
	 * org.osgi.framework;version="[1.6,2)",org.osgi.framework.wiring;version="[
	 * 1.0,2)",org.osgi.service.async;version="[1.0,1.1)",org.osgi.service.async
	 * .delegate;version="[1.0,2)",org.osgi.service.log;version="[1.3,2)",org.
	 * osgi.util.promise;version="[1.0,1.1)",org.osgi.util.tracker;version="[1.5
	 * ,2)" Tool: Bnd-3.0.0.201506011706
	 * </pre>
	 *
	 * So effectively the offer to import org.osgi.util.promise is broken. If
	 * the framework wanted to resolve the bundle by importing
	 * org.osgi.util.promise, that package has a uses constraint on
	 * org.osgi.util.function and since the bundle only exports
	 * org.osgi.util.function, the framework can only resolve the bundle to
	 * another exporter of org.osgi.util.promise if that exporter imports
	 * org.osgi.util.function from this bundle. Obviously that wont work for
	 * additional bundle attempting the same thing. bnd fails to also import
	 * org.osgi.util.function. This issue exists in bnd 2.4.1 and master. We
	 * have 4 packages
	 *
	 * <pre>
	 * p1 -> p2 exported (makes p2 importable) p2 -> none exported (force to
	 * import by p1) p3 -> p1 private (makes p1 importable) p4 -> p3 exported
	 * (p4 cannot be imported due to private ref)
	 *
	 * <pre>
	 */

	@Test
	public void testNoImportForUsedExport_971() throws Exception {
		// exports with version should be added to imports
		Builder b = new Builder();
		b.addClasspath(new File("bin_test"));
		b.setExportPackage(
			"test.missingimports_971.p1;version=1.1.0,test.missingimports_971.p2;version=1.1.0,test.missingimports_971.p4;version=1.1.0");
		b.setPrivatePackage("test.missingimports_971.p3");
		b.build();
		assertTrue(b.check());

		assertTrue(b.getExports()
			.containsFQN("test.missingimports_971.p1"));
		assertTrue(b.getExports()
			.containsFQN("test.missingimports_971.p2"));
		assertTrue(b.getExports()
			.containsFQN("test.missingimports_971.p4"));
		assertTrue(b.getImports()
			.containsFQN("test.missingimports_971.p1"));
		assertTrue(b.getImports()
			.containsFQN("test.missingimports_971.p2"));
		b.getJar()
			.getManifest()
			.write(System.out);
	}


	/**
	 * Counterpart of {@link #testNoImportForUsedExport_971()}
	 *
	 * @throws Exception
	 */
	@Test
	public void testEnsureNoImportForUsedExport_971_WithMissingExportVersion() throws Exception {
		// exports without version should not be added to imports
		Builder b = new Builder();
		b.addClasspath(new File("bin_test"));
		b.setExportPackage(
			"test.missingimports_971.p1,test.missingimports_971.p2,test.missingimports_971.p4");
		b.setPrivatePackage("test.missingimports_971.p3");
		b.build();
		assertTrue(b.check());

		assertTrue(b.getExports()
			.containsFQN("test.missingimports_971.p1"));
		assertTrue(b.getExports()
			.containsFQN("test.missingimports_971.p2"));
		assertTrue(b.getExports()
			.containsFQN("test.missingimports_971.p4"));
		assertFalse(b.getImports()
			.containsFQN("test.missingimports_971.p1"));
		assertFalse(b.getImports()
			.containsFQN("test.missingimports_971.p2"));
		b.getJar()
			.getManifest()
			.write(System.out);
	}

	/*
	 * Private package header doesn't allow the use of negation (!) #840
	 */

	@Test
	public void testNegationInPrivatePackage_840() throws Exception {
		Builder b = new Builder();
		b.setProperty(Constants.STRICT, "true");
		b.addClasspath(IO.getFile("jar/osgi.jar"));
		b.setPrivatePackage("!org.osgi.service.event,org.osgi.service.*");
		b.build();
		assertTrue(b.check(
			"Import Package clauses without version range: \\[javax.servlet, javax.microedition.io, javax.servlet.http\\]"));
	}

	/*
	 * Warn about missing packages in export
	 */

	@Test
	public void testWarnAboutMissingExports() throws Exception {
		Builder b = new Builder();
		b.setProperty(Constants.STRICT, "true");
		b.addClasspath(IO.getFile("jar/osgi.jar"));
		b.setIncludeResource("foo;literal='bla'"); // get rid of warningt
		b.setExportPackage("bar");
		b.build();
		assertTrue(b.check("\\QExport-Package or -exportcontents refers to missing package 'bar'\\E"));
	}

	/*
	 * Warn about imports to private imports
	 */

	@Test
	public void testWarnAboutPrivateImportsFromExport() throws Exception {
		Builder p3 = setupP3();
		p3.setExportPackage("test.activator;version=2");
		p3.setPrivatePackage("test.activator.inherits");
		p3.build();
		assertTrue(p3.check("Import package org\\.osgi\\..* not found in any bundle "));

		p3.getJar()
			.getManifest()
			.write(System.out);
	}

	/*
	 * Warn about imports to private imports
	 */

	@Test
	public void testWarnAboutPrivateImports() throws Exception {
		Builder p3 = setupP3();
		p3.addClasspath(new File("jar/osgi.jar"));
		p3.setExportPackage("test.activator.inherits;version=0.0.0");
		p3.build();
		assertTrue(p3.check("Import package test.activator not found in any bundle on the -buildpath."));

		p3.getJar()
			.getManifest()
			.write(System.out);
	}

	private Builder setupP3() throws Exception {
		Builder p1 = new Builder();
		p1.setProperty("Bundle-SymbolicName", "p1");
		p1.setProperty("Bundle-Version", "1.2.3");
		p1.setPrivatePackage("test.activator");
		p1.addClasspath(new File("bin_test"));
		p1.addClasspath(new File("jar/osgi.jar"));
		p1.build();
		assertTrue(p1.check());

		Builder p2 = new Builder();
		p2.setProperty("Bundle-SymbolicName", "p2");
		p2.setExportPackage("test.activator.inherits");
		p2.addClasspath(new File("bin_test"));
		p2.build();
		assertTrue(p2.check());

		Builder p3 = new Builder();
		p3.setProperty("Bundle-SymbolicName", "p3");
		p3.setProperty("-check", "ALL");
		p3.addClasspath(p1.getJar());
		p3.addClasspath(p2.getJar());
		return p3;
	}

	/**
	 * #708 if a bundle has a.b.c but imports a.b then bnd cannot find the
	 * version of a.b because the scanning of a.b.c already has set the
	 * information for a.b to "nothing". The learnPackage() method must be
	 * adapted so that "empty" package do not occupy a position This was
	 * diagnosed by Balázs Zsoldos balazs.zsoldos@everit.biz
	 *
	 * @throws Exception
	 */

	@Test
	public void testOverlappingPackageMissesImportVersions() throws Exception {
		Builder exporter = new Builder();
		exporter.setExportPackage("test._708.a.b");
		exporter.addClasspath(new File("bin_test"));
		exporter.build();
		assertTrue(exporter.check());

		//
		// We need to build a temp entry because if we include 'bin'
		// on the final build then we see the a.b package there, and
		// there it has information
		//

		Builder temp = new Builder();
		temp.setPrivatePackage("test._708.a.b.c");
		temp.addClasspath(new File("bin_test"));
		temp.build();
		assertTrue(temp.check());

		Builder importer = new Builder();
		importer.setPrivatePackage("test._708.a.b.c");
		importer.addClasspath(temp.getJar());
		importer.addClasspath(exporter.getJar());
		importer.setProperty("-noimportjava", "true");

		importer.build();
		assertTrue(importer.check());

		assertEquals("test._708.a.b;version=\"1.2.3\"", exporter.getJar()
			.getManifest()
			.getMainAttributes()
			.getValue("Export-Package"));
		assertEquals("test._708.a.b;version=\"[1.2,2)\"", importer.getJar()
			.getManifest()
			.getMainAttributes()
			.getValue("Import-Package"));

	}

	/**
	 * Test if the Manifest gets the last modified date
	 */

	@Test
	public void testLastModifiedForManifest(@InjectTemporaryDirectory
	File tmp) throws Exception {
		long time = System.currentTimeMillis();

		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setExportPackage("org.osgi.framework");
			Jar build = b.build();
			assertTrue(b.check());

			File file = new File(tmp, "tmp.jar");
			build.write(file);
			try (Jar ajr = new Jar(file)) {
				Resource r = ajr.getResource("META-INF/MANIFEST.MF");
				assertNotNull(r);
				long t = r.lastModified();
				Date date = new Date(t);
				System.out.println(date + " " + t);
				// TODO we need to adapt the timestamp handling
				assertThat(t).as("%s %s", date, t)
					.isEqualTo(1142555622000L);
			}
		}
	}

	/**
	 * A Require-Bundle should remove imports that are exported by its target(s)
	 *
	 * @throws Exception
	 */

	@Test
	public void testRemovedImportWithRequireBundle() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(new File("bin_test"));
			b.addClasspath(new File("jar/osgi.core.jar"));
			b.setPedantic(true);

			// causes an import to org.osgi.framework and javax.swing
			b.setExportPackage("test.classreference;version=1,test.classreferencetoosgijar;version=1");

			// the require bundle will then remove the osgi ref
			b.setProperty("Require-Bundle", "osgi.core");
			b.setProperty("-noimportjava", "true");
			b.build();
			assertThat(b.getImports()
				.keySet()).containsExactlyInAnyOrder(b.getPackageRef("javax.swing"));

			try (Verifier v = new Verifier(b.getJar())) {
				v.verify();
				assertTrue(v.check("Host .* for this fragment"));
			}
		}
	}

	/**
	 * #479 I have now tested this locally. Apparently the fix doesn't change
	 * the reported behavior. In my test bundle, I have removed all imports. Bnd
	 * builds this with no errors reported. I add: DynamicImport-Package: dummy
	 */

	@Test
	public void testMissingImportsWithDynamicImport() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(new File("bin_test"));
			b.setPedantic(true);
			b.setExportPackage("test.classreference;version=1");
			b.setImportPackage("!*");
			b.setProperty(Constants.DYNAMICIMPORT_PACKAGE, "dummy");
			b.build();
			assertTrue(b.check());

			try (Verifier v = new Verifier(b.getJar())) {
				v.verify();
				assertTrue(v.check("Unresolved references to \\[javax.swing\\] by class\\(es\\)"));
			}
		}
	}

	/**
	 * #479 I have now tested this locally. Apparently the fix doesn't change
	 * the reported behavior. In my test bundle, I have removed all imports. Bnd
	 * builds this with no errors reported. I add: DynamicImport-Package: dummy
	 */

	@Test
	public void testMissingImportsWithoutDynamicImport() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(new File("bin_test"));
			b.setPedantic(true);
			b.setExportPackage("test.classreference;version=1");
			b.setImportPackage("!*");
			b.build();
			assertTrue(b.check());

			try (Verifier v = new Verifier(b.getJar())) {
				v.verify();
				assertTrue(v.check("Unresolved references to \\[javax.swing\\] by class\\(es\\)"));
			}
		}

	}

	/**
	 * <pre>
	 *  [2013-12-11 15:55:14] BJ Hargrave: init: [echo] Enter project
	 * org.osgi.test.cases.prefs (${top}) [bndprepare] 2 WARNINGS [bndprepare]
	 * No translation found for macro:
	 * classes;extending;junit.framework.TestCase;concrete [bndprepare] No
	 * translation found for macro: classes,concrete [2013-12-11 15:55:31] BJ
	 * Hargrave: I am getting this on the latest bnd.master in the OSGi test
	 * projects
	 * </pre>
	 *
	 * @throws Exception
	 */

	@Test
	public void testClassQuery() throws Exception {
		try (Builder a = new Builder()) {
			a.addClasspath(new File("bin_test"));
			a.setExportPackage("test.component");
			a.setProperty("testcases",
				"${sort;${uniq;${classes;EXTENDS;junit.framework.TestCase;CONCRETE};${classes;HIERARCHY_ANNOTATED;org.junit.Test;CONCRETE};${classes;HIERARCHY_INDIRECTLY_ANNOTATED;org.junit.platform.commons.annotation.Testable;CONCRETE}}}");
			a.setProperty("Test-Cases", "${testcases}");
			a.setProperty("-dsannotations", "!*");
			a.setProperty("-metatypeannotations", "!*");
			Jar jar = a.build();
			assertTrue(a.check());
			Manifest m = jar.getManifest();
			Parameters p = new Parameters(m.getMainAttributes()
				.getValue("Test-Cases"));
			assertThat(p).hasSizeGreaterThanOrEqualTo(4);
		}
	}

	/**
	 * Bundle ActivationPolicy
	 *
	 * @throws Exception
	 */
	@Test
	public void testBundleActivationPolicy() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("bin_test"));

			b.setProperty("Bundle-ActivationPolicy", "lazy");
			b.setProperty("Export-Package", "test.activator");
			b.build();
			assertTrue(b.check());
		} finally {
			b.close();
		}
	}

	/**
	 * #388 Manifest header to get GIT head
	 *
	 * @throws IOException
	 */
	@Test
	public void testGitHead() throws IOException {
		Builder b = new Builder();
		try {
			String s = b.getReplacer()
				.process("${githead}");
			assertTrue(Hex.isHex(s));
		} finally {
			b.close();
		}
	}

	/**
	 * If a package-info.java + packageinfo are present then normally
	 * package-info takes precedence if it sets a Version. This test sees that
	 * if no version is sets, packageinfo is used.
	 */
	@Test
	public void testPackageInfo_no_version() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(new File("bin_test"));
			b.setExportPackage("test.packageinfo.both_no_version");
			Jar build = b.build();
			assertTrue(b.check());

			Attrs imports = b.getExports()
				.getByFQN("test.packageinfo.both_no_version");
			assertEquals("1.2.3", imports.getVersion());
		} finally {
			b.close();
		}

	}

	/**
	 * An old osgi 3.0.0 jar had an old packageinfo in it. This included some
	 * never well developed syntax which now clashes with the proprty syntax.
	 *
	 * @throws Exception
	 */
	@Test
	public void testVeryOldPackageInfo() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi-3.0.0.jar"));
			b.setExportPackage("org.osgi.util.measurement;version=100, org.osgi.util.tracker;version=100, *");
			Jar build = b.build();
			assertTrue(
				b.check("Version for package org.osgi.util.measurement is set to different values in the source ",
					"Version for package org.osgi.util.tracker is set to different values in the source"));
		} finally {
			b.close();
		}

	}

	/**
	 * Using a package info without the version keyword gives strange results in
	 * the manifest, should generate an error.
	 */

	@Test
	public void testBadPackageInfo() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(new File("bin_test"));
			b.setExportPackage("test.package_info_versioniskey");
			b.build();

			String message = b.getErrors()
				.get(0);
			assertThat(message).as("The lacking version error first")
				.contains("package info for test.package_info_versioniskey attribute [1.0.0=''],");
			Location location = b.getLocation(message);
			assertNotNull(location, "Supposed to have a location");
			assertNotNull(location.file, "And that must have a file");
			assertEquals("packageinfo", new File(location.file).getName(), "Which should be the packaginfo file");
			assertEquals(4, location.line);
			assertEquals(5, location.length);

			assertTrue(b.check("package info for test.package_info_versioniskey attribute \\[1.0.0=''\\],"));
		} finally {
			b.close();
		}
	}

	/**
	 * https://github.com/bndtools/bnd/issues/359 Starts with a bundle that has
	 * one package 'a' containing classes A and B. First removes B from 'a', and
	 * checks that the last modified date of the resulting bundle changed. Then
	 * removes A from 'a', and checks again that the last modified data changed.
	 */
	@Test
	public void testRemoveClassFromPackage(@InjectTemporaryDirectory
	File tmp) throws Exception {
		try (Builder b = new Builder()) {
			IO.mkdirs(IO.getFile(tmp, "a"));
			IO.copy(IO.getFile("bin_test/a/A.class"), IO.getFile(tmp, "a/A.class"));
			IO.copy(IO.getFile("bin_test/a/B.class"), IO.getFile(tmp, "a/B.class"));
			Jar classpath = new Jar(tmp);
			b.addClasspath(classpath);
			b.setPrivatePackage("a");
			Jar result = b.build();
			Resource ra = result.getResource("a/A.class");
			assertThat(ra).isNotNull();
			Resource rb = result.getResource("a/B.class");
			assertThat(rb).isNotNull();
			long lm1 = result.lastModified();
			assertThat(lm1).as("Last modified date of bundle > 0")
				.isGreaterThan(0L);

			// windows has a very low resolution sometimes
			Thread.sleep(IO.isWindows() ? 1000 : 100);

			IO.delete(IO.getFile(tmp, "a/B.class"));
			classpath.remove("a/B.class");
			classpath.updateModified(System.currentTimeMillis(), "Removed file B");
			result = b.build();
			long lm2 = result.lastModified();
			assertThat(lm2).as("Last modified date of bundle has increased after deleting class from package")
				.isGreaterThan(lm1);

			// windows has a very low resolution sometimes
			Thread.sleep(IO.isWindows() ? 1000 : 100);

			IO.delete(IO.getFile(tmp, "a/A.class"));
			classpath.remove("a/A.class");
			classpath.updateModified(System.currentTimeMillis(), "Removed file A");

			// windows has a very low resolution sometimes
			Thread.sleep(IO.isWindows() ? 1000 : 100);

			result = b.build();
			long lm3 = result.lastModified();
			assertThat(lm3).as("Last modified date of bundle has increased after deleting last class from package")
				.isGreaterThan(lm2);
		}
	}

	/**
	 * https://github.com/bndtools/bnd/issues/315 Turns out bnd doesn't seem to
	 * support a class in a capitalized package name. I accidentally called a
	 * package with a capital letter and I get the strange error message and a
	 * refusal to build it. (See title for error message) My package could be
	 * named "Coffee" and the package named "CoffeeClient", The bnd.bnd file
	 * could have: Private-Package: Coffee I'm running 2.0.0REL with Eclipse
	 * Juno.
	 */
	@Test
	public void testUpperCasePackage() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("bin_test"));
			b.setExportPackage("UPPERCASEPACKAGE");
			b.build();
			assertTrue(b.check());
			b.getExports()
				.containsFQN("UPPERCASEPACKAGE");
		} finally {
			b.close();
		}
	}

	public @interface TestAnnotation {}

	@TestAnnotation
	public static class Target {}

	/**
	 * Dave Smith <dave.smith@candata.com> I have pulled the latest from git and
	 * am testing out 2.0 with our current application. I am getting the
	 * following error message on the bnd.bnd file null, for cmd : classes,
	 * arguments [classes;CONCRETE;ANNOTATION;javax.persistence.Entity] My bnd
	 * file does have the following line ... Hibernate-Db =
	 * ${classes;CONCRETE;ANNOTATION;javax.persistence.Entity}
	 *
	 * @throws Exception
	 */

	@Test
	public void testClasses() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("x", "${classes;CONCRETE;ANNOTATION;test.BuilderTest$TestAnnotation}");
			b.setProperty("y", "${classes;CONCRETE;ANNOTATED;test.BuilderTest$TestAnnotation}");
			b.setProperty("z", "${classes;CONCRETE;ANNOTATEDX;x.y.Z}");
			b.setPrivatePackage("test");
			b.addClasspath(IO.getFile("bin_test"));
			b.build();
			String s = b.getProperty("x");
			assertEquals(s, b.getProperty("y"));
			assertTrue(s.contains("test.BuilderTest$Target"));
			assertEquals("${classes;CONCRETE;ANNOTATEDX;x.y.Z}", b.getProperty("z"));
			assertTrue(b.check("ANNOTATEDX"));
		} finally {
			b.close();
		}
	}

	/**
	 * Check if we can create digests
	 *
	 * @throws Exception
	 */

	@Test
	public void testDigests(@InjectTemporaryDirectory
	File tmp) throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setExportPackage("org.osgi.framework");
			b.setProperty(Constants.DIGESTS, "MD5, SHA1");
			Jar jar = b.build();
			assertTrue(b.check());
			File f = File.createTempFile("test", ".jar", tmp);
			jar.write(f);

			Jar other = new Jar(f);
			Manifest manifest = other.getManifest();
			assertNotNull(manifest);
			Attributes attrs = manifest.getAttributes("org/osgi/framework/BundleActivator.class");
			assertNotNull(attrs);
			assertEquals("RTRhr3kadnulINegRhpmog==", attrs.getValue("MD5-Digest"));
			assertEquals("BfVfpnE3Srx/0UWwtzNecrAGf8A=", attrs.getValue("SHA1-Digest"));
			other.close();
		} finally {
			b.close();
		}
	}

	/**
	 * FELIX-3407 Utterly confusing example that states that generic references
	 * are not picked up. The classes under test are in
	 * {@link test.genericinterf.a.A}, {@link test.genericinterf.b.B}, and
	 * {@link test.genericinterf.c.C}.
	 */
	@Test
	public void testGenericPickup() throws Exception {
		Builder b = new Builder();
		try {
			b.setPrivatePackage("test.genericinterf.a");
			b.addClasspath(new File("bin_test"));
			b.build();
			assertTrue(b.check());
			System.out.println(b.getImports());
			assertTrue(b.getImports()
				.containsFQN("test.genericinterf.b"));
			assertTrue(b.getImports()
				.containsFQN("test.genericinterf.c"));
		} finally {
			b.close();
		}
	}

	/**
	 * Github #130 Consider the following descriptor file: Bundle-Activator:
	 * org.example.Activator Private-Package: org.example Now suppose that at
	 * build time, bnd cannot find the package org.example, or it is empty. Bnd
	 * sees the Bundle-Activator instruction as creating a dependency, so it
	 * generates a manifest containing an import for that package:
	 * Import-Package: org.example This is unexpected. If a Private-Package
	 * instruction is given with a specific package name (i.e. not a wildcard),
	 * and that package does not exist or is empty, then bnd should fail or
	 * print an error.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPrivatePackageNonExistent() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setBundleActivator("com.example.Activator");
			b.setPrivatePackage("com.example");
			b.setIncludeResource("p;literal='x'");

			b.build();
			assertTrue(b.check("on the class path: \\[com.example\\]",
				"Bundle-Activator com.example.Activator is being imported"));
		} finally {
			b.close();
		}
	}

	/**
	 * #41 Test the EE macro
	 */

	@Test
	public void testEEMacro() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/ifc112.jar"));
			b.setPrivatePackage("netscape.util.*");
			b.setBundleRequiredExecutionEnvironment("${ee}");
			Jar jar = b.build();
			assertTrue(b.check());

			Domain domain = Domain.domain(jar.getManifest());
			Parameters ee = domain.getBundleRequiredExecutionEnvironment();
			System.err.println(ee);
			assertTrue(ee.containsKey("JRE-1.1"));
		} finally {
			b.close();
		}
	}

	private static final Pattern FILTER_VERSION = Pattern.compile(
		"\\(&\\(osgi\\.ee=(?<osgiee>JavaSE|JRE)\\)\\(version=(?<version>" + Version.VERSION_STRING + ")\\)\\)");

	@ParameterizedTest(name = "package={0}, bree={1}, osgi.ee={2}, version={3}")
	@ArgumentsSource(CompilerVersionsArgumentsProvider.class)
	@DisplayName("${ee} Macro Testing")
	public void testEEMacro2(String pkg, String bree, String osgiee, String version) throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("compilerversions/compilerversions.jar"));
			b.setPrivatePackage(pkg);
			b.setBundleRequiredExecutionEnvironment("${ee}");
			Jar jar = b.build();
			assertTrue(b.check());
			Domain domain = Domain.domain(jar.getManifest());
			Parameters ee = domain.getBundleRequiredExecutionEnvironment();
			assertThat(ee).hasToString(bree + "-" + version);

			//
			// Check the requirements
			//
			Parameters een = domain.getRequireCapability();
			assertThat(een).isNotEmpty();
			Attrs attrs = een.get("osgi.ee");
			String filter = attrs.get("filter:");
			Matcher m = FILTER_VERSION.matcher(filter);
			assertTrue(m.matches());
			assertThat(m.group("osgiee")).isEqualTo(osgiee);
			assertThat(new Version(m.group("version"))).isEqualTo(new Version(version));
		}
	}

	static class CompilerVersionsArgumentsProvider implements ArgumentsProvider {
		@Override
		public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
			FileTree tree = new FileTree();
			Stream<File> files = tree.stream(new File("compilerversions/src"), "*");
			return files.filter(File::isDirectory)
				.map(File::getName)
				.map(pkg -> {
					String[] split = Strings.first(pkg, '_');
					Version v = split[1].equals("jsr14") ? new Version(1, 4)
						: Version.parseVersion(split[1].replace('_', '.'));
					String bree = "JavaSE";
					String osgiee = "JavaSE";
					String version;
					if (v.getMajor() == 1) {
						version = "1." + v.getMinor();
						if (v.getMinor() == 1) {
							bree = osgiee = "JRE";
						} else if (v.getMinor() <= 5) {
							bree = "J2SE";
						}
					} else {
						version = Integer.toString(v.getMajor());
					}
					return Arguments.of(pkg, bree, osgiee, version);
				});
		}
	}

	/**
	 * bnd issues Consider the following descriptor file:
	 *
	 * <pre>
	 * Bundle-Activator: org.example.Activator Private-Package: org.example
	 * </pre>
	 *
	 * Now suppose that at build time, bnd cannot find the package org.example,
	 * or it is empty. Bnd sees the Bundle-Activator instruction as creating a
	 * dependency, so it generates a manifest containing an import for that
	 * package:
	 *
	 * <pre>
	 *  Import-Package: org.example
	 * </pre>
	 *
	 * This is unexpected. If a Private-Package instruction is given with a
	 * specific package name (i.e. not a wildcard), and that package does not
	 * exist or is empty, then bnd should fail or print an error.
	 */

	@Test
	public void testReportEmptyPrivatePackage() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(new File("bin_test"));
			b.setPrivatePackage("does.not.exist");
			b.build();
			assertTrue(b.check("The JAR is empty", "Unused -privatepackage instruction"));
		} finally {
			b.close();
		}
	}

	/**
	 * Test the name section
	 */

	@Test
	public void testNamesection() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty(Constants.NAMESECTION,
				"org/osgi/service/event/*;MD5='${md5;${@}}';SHA1='${sha1;${@}}';MD5H='${md5;${@};hex}'");
			b.setProperty(Constants.PRIVATEPACKAGE, "org.osgi.service.event");
			Jar build = b.build();
			assertOk(b);
			build.calcChecksums(new String[] {
				"MD5", "SHA1"
			});
			assertTrue(b.check());
			Manifest m = build.getManifest();
			m.write(System.err);

			assertNotNull(m.getAttributes("org/osgi/service/event/EventAdmin.class")
				.getValue("MD5"));
			assertNotNull(m.getAttributes("org/osgi/service/event/EventAdmin.class")
				.getValue("SHA1"));
			assertEquals(m.getAttributes("org/osgi/service/event/EventAdmin.class")
				.getValue("MD5-Digest"),
				m.getAttributes("org/osgi/service/event/EventAdmin.class")
					.getValue("MD5"));
		} finally {
			b.close();
		}

	}

	@Test
	public void testPackageNamesection() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty(Constants.NAMESECTION, "org/osgi/service/event/;Foo=bar");
			b.setProperty(Constants.PRIVATEPACKAGE, "org.osgi.service.event");
			Jar build = b.build();
			assertOk(b);
			assertTrue(b.check());
			Manifest m = build.getManifest();
			m.write(System.err);

			assertNotNull(m.getAttributes("org/osgi/service/event/")
				.getValue("Foo"));
		} finally {
			b.close();
		}

	}

	@Test
	public void testGlobPackageNamesection() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty(Constants.NAMESECTION, "org/osgi/service/*/;Foo=bar");
			b.setProperty(Constants.PRIVATEPACKAGE, "org.osgi.service.event");
			Jar build = b.build();
			assertOk(b);
			assertTrue(b.check());
			Manifest m = build.getManifest();
			m.write(System.err);

			assertNotNull(m.getAttributes("org/osgi/service/event/")
				.getValue("Foo"));
		} finally {
			b.close();
		}

	}

	/**
	 * Check of the use of x- directives are not skipped. bnd allows x-
	 * directives in the import/export clauses but strips other ones.
	 *
	 * @throws Exception
	 */
	@Test
	public void testXDirectives() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty("Export-Package", "org.osgi.framework;x-foo:=true;bar:=false");
			Jar jar = b.build();
			assertTrue(b.check("bar:"));
			Manifest m = jar.getManifest();
			String s = m.getMainAttributes()
				.getValue("Export-Package");
			assertTrue(s.contains("x-foo:"));
		} finally {
			b.close();
		}
	}

	/**
	 * Check of SNAPSHOT is replaced with the -snapshot instr
	 *
	 * @throws Exception
	 */
	@Test
	public void testSnapshot() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("-resourceonly", "true");
			b.setProperty("-snapshot", "TIMESTAMP");
			b.setProperty("Bundle-Version", "1.0-SNAPSHOT");
			Jar jar = b.build();
			assertTrue(b.check("The JAR is empty"));
			Manifest m = jar.getManifest();
			assertEquals("1.0.0.TIMESTAMP", m.getMainAttributes()
				.getValue("Bundle-Version"));
		} finally {
			b.close();
		}
	}

	/**
	 * Check if do not copy works on files
	 */

	@Test
	public void testDoNotCopy() throws Exception {
		try (Builder b = new Builder()) {
			b.setProperty("-resourceonly", "true");
			b.setProperty("-donotcopy", ".*\\.[jw]ar|\\..*");
			b.setProperty("Include-Resource", "jar");
			b.build();
			assertTrue(b.check());

			Set<String> names = b.getJar()
				.getResources()
				.keySet();
			System.out.println(names);
			assertThat(names)
				.contains("AnnotationWithJSR14.jclass", "mandatorynoversion.bnd", "mina.bar", "minax.bnd", "rox.bnd",
					"WithAnnotations.jclass")
				.noneMatch(name -> name.endsWith(".jar"))
				.noneMatch(name -> name.endsWith(".war"));
		}
	}

	/**
	 * Check if do not copy works on files
	 */

	@Test
	public void testDoNotCopyDS() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("-resourceonly", "true");
			b.setProperty("Include-Resource", "jar/");
			b.build();
			assertTrue(b.check());

			Set<String> names = b.getJar()
				.getResources()
				.keySet();
			assertFalse(names.contains(".DS_Store"));
		} finally {
			b.close();
		}
	}

	/**
	 * No error is generated when a file is not found.
	 */

	@Test
	public void testFileNotFound() throws Exception {
		Builder b = new Builder();
		try {
			b.setPedantic(true);
			b.setProperty("-classpath", "xyz.jar");
			b.setProperty("Include-Resource", "lib=lib, jar/osgi.jar");
			b.setProperty("-resourceonly", "true");
			b.build();
			assertTrue(b.check("Input file does not exist: lib", "Cannot find entry on -classpath: xyz.jar"));
		} finally {
			b.close();
		}
	}

	/**
	 * bnd seems to pick the wrong version if a packageinfo is available
	 * multiple times.
	 *
	 * @throws Exception
	 */

	@Test
	public void testMultiplePackageInfo() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(IO.getFile("jar/osgi.core.jar"));
			b.setProperty(Constants.PRIVATEPACKAGE, "org.osgi.service.packageadmin;-split-package:=first");
			b.build();
			assertTrue(b.check());
			String version = b.getImports()
				.getByFQN("org.osgi.framework")
				.get(Constants.VERSION_ATTRIBUTE);
			assertEquals("[1.3,2)", version);
		} finally {
			b.close();
		}
	}

	/**
	 * Test the from: directive on expanding packages.
	 */
	@Test
	public void from_directive() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(IO.getFile("jar/org.eclipse.osgi-3.5.0.jar"));
			b.setProperty("Export-Package", "org.osgi.framework;from:=osgi");
			b.build();
			assertTrue(b.check());

			assertThat(b.getExports()
				.getByFQN("org.osgi.framework")).containsEntry("version", "1.3")
					.containsEntry(Constants.FROM_DIRECTIVE, "osgi");
		}
	}

	@Test
	public void negated_from_directive() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("jar/org.eclipse.osgi-3.5.0.jar"));
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty("-includepackage", "org.osgi.framework;from:=!org.eclipse.osgi*");
			b.setProperty("-exportcontents", "org.osgi.framework");
			b.build();
			assertTrue(b.check("Version for package*"));

			assertThat(b.getExports()
				.getByFQN("org.osgi.framework")).containsEntry("version", "1.3")
					.extractingByKey(Constants.FROM_DIRECTIVE, InstanceOfAssertFactories.STRING)
					.contains("osgi.jar");
		}
	}

	@Test
	public void testFromEclipseDirective() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(IO.getFile("jar/org.eclipse.osgi-3.5.0.jar"));
			b.setProperty("Export-Package", "org.osgi.framework;from:=org.eclipse.osgi-3.5.0");
			b.build();
			assertTrue(b.check());

			assertEquals("1.3", b.getExports()
				.getByFQN("org.osgi.framework")
				.get("version"));
		} finally {
			b.close();
		}
	}

	/**
	 * Test the provide package
	 */
	@Test
	public void testProvidedVersion() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(new File("bin_test"));
			b.setProperty(Constants.EXPORT_PACKAGE, "org.osgi.service.event;provide:=true");
			b.setProperty("Private-Package", "test.refer");
			Jar jar = b.build();
			assertTrue(b.check());
			String ip = jar.getManifest()
				.getMainAttributes()
				.getValue(Constants.IMPORT_PACKAGE);
			Parameters map = Processor.parseHeader(ip, null);
			assertEquals("[1.0,1.1)", map.get("org.osgi.service.event")
				.get("version"));
		} finally {
			b.close();
		}
	}

	@Test
	public void testUnProvidedVersion() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(new File("bin_test"));
			b.setProperty(Constants.EXPORT_PACKAGE, "org.osgi.service.event;provide:=false");
			b.setProperty("Private-Package", "test.refer");
			Jar jar = b.build();
			assertTrue(b.check());
			String ip = jar.getManifest()
				.getMainAttributes()
				.getValue(Constants.IMPORT_PACKAGE);
			Parameters map = Processor.parseHeader(ip, null);
			assertEquals("[1.0,2)", map.get("org.osgi.service.event")
				.get("version"));
		} finally {
			b.close();
		}
	}

	/**
	 * Complaint that exported versions were not picked up from external bundle.
	 */

	@Test
	public void testExportedVersionsNotPickedUp() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/jsr311-api-1.1.1.jar"));
			b.setProperty("Export-Package", "javax.ws.rs.core");
			Jar jar = b.build();
			assertTrue(b.check());
			String ip = jar.getManifest()
				.getMainAttributes()
				.getValue(Constants.EXPORT_PACKAGE);
			Parameters map = Processor.parseHeader(ip, null);
			assertEquals("1.1.1", map.get("javax.ws.rs.core")
				.get("version"));
		} finally {
			b.close();
		}
	}

	/**
	 * Test where the version comes from: Manifest or packageinfo
	 *
	 * @throws Exception
	 */
	@Test
	public void testExportVersionSource() throws Exception {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes()
			.putValue("Export-Package", "org.osgi.service.event;version=100");

		// Remove packageinfo
		Jar manifestOnly = new Jar(IO.getFile("jar/osgi.jar"));
		try {
			manifestOnly.remove("org/osgi/service/event/packageinfo");
			manifestOnly.setManifest(manifest);

			// Remove manifest
			Jar packageInfoOnly = new Jar(IO.getFile("jar/osgi.jar"));
			packageInfoOnly.setManifest(new Manifest());

			Jar both = new Jar(IO.getFile("jar/osgi.jar"));
			both.setManifest(manifest);

			// Only version in manifest
			Builder bms = new Builder();
			try {
				bms.addClasspath(manifestOnly);
				bms.setProperty("Export-Package", "org.osgi.service.event");
				bms.build();
				assertTrue(bms.check());

				String s = bms.getExports()
					.getByFQN("org.osgi.service.event")
					.get("version");
				assertEquals("100", s);

				// Only version in packageinfo
				Builder bpinfos = new Builder();
				bpinfos.addClasspath(packageInfoOnly);
				bpinfos.setProperty("Export-Package", "org.osgi.service.event");
				bpinfos.build();
				assertTrue(bpinfos.check());

				s = bpinfos.getExports()
					.getByFQN("org.osgi.service.event")
					.get("version");
				assertEquals("1.0.1", s);
			} finally {
				bms.close();
			}
		} finally {
			manifestOnly.close();
		}

	}

	/**
	 * Test where the version comes from: Manifest or packageinfo
	 *
	 * @throws Exception
	 */
	@Test
	public void testImportVersionSource() throws Exception {
		Jar fromManifest = new Jar("manifestsource");
		Jar fromPackageInfo = new Jar("packageinfosource");
		Jar fromBoth = new Jar("both");
		try {
			Manifest mms = new Manifest();
			mms.getMainAttributes()
				.putValue("Export-Package", "org.osgi.service.event; version=100");
			fromManifest.setManifest(mms);

			fromPackageInfo.putResource("org/osgi/service/event/packageinfo", new EmbeddedResource("version 99", 0L));

			Manifest mboth = new Manifest();
			mboth.getMainAttributes()
				.putValue("Export-Package", "org.osgi.service.event; version=101");
			fromBoth.putResource("org/osgi/service/event/packageinfo", new EmbeddedResource("version 199", 0L));
			fromBoth.setManifest(mboth);

			// Only version in manifest
			Builder bms = new Builder();
			try {
				bms.addClasspath(fromManifest);
				bms.setProperty("Import-Package", "org.osgi.service.event");
				bms.build();
				assertTrue(bms.check("The JAR is empty"));
				String s = bms.getImports()
					.getByFQN("org.osgi.service.event")
					.get("version");
				assertEquals("[100.0,101)", s);
				// Only version in packageinfo
				Builder bpinfos = new Builder();
				try {
					bpinfos.addClasspath(fromPackageInfo);
					bpinfos.setProperty("Import-Package", "org.osgi.service.event");
					bpinfos.build();
					assertTrue(bms.check());
					s = bpinfos.getImports()
						.getByFQN("org.osgi.service.event")
						.get("version");
					assertEquals("[99.0,100)", s);

					// Version in manifest + packageinfo
					Builder bboth = new Builder();
					try {
						bboth.addClasspath(fromBoth);
						bboth.setProperty("Import-Package", "org.osgi.service.event");
						bboth.build();
						assertTrue(bms.check());
						s = bboth.getImports()
							.getByFQN("org.osgi.service.event")
							.get("version");
						assertEquals("[101.0,102)", s);
					} finally {
						bboth.close();
					}
				} finally {
					bpinfos.close();
				}
			} finally {
				bms.close();
			}

		} finally {
			fromManifest.close();
			fromPackageInfo.close();
			fromBoth.close();
		}
	}

	@Test
	public void testNoImportDirective() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("Export-Package", "org.osgi.util.measurement, org.osgi.service.http;-noimport:=true");
			b.setProperty("Private-Package", "org.osgi.framework, test.refer");
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(new File("bin_test"));
			Jar jar = b.build();
			assertTrue(b.check());

			Manifest m = jar.getManifest();
			String imports = m.getMainAttributes()
				.getValue("Import-Package");
			assertTrue(imports.contains("org.osgi.util.measurement")); // referred
																		// to
																		// but
																		// no
																		// private
																		// references
																		// (does
																		// not
																		// use
																		// fw).
			assertFalse(imports.contains("org.osgi.service.http")); // referred
																	// to
																	// but no
																	// private
																	// references
																	// (does not
																	// use
																	// fw).
		} finally {
			b.close();
		}

	}

	@Test
	public void testNoImportDirective2() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("Export-Package", "org.osgi.util.measurement;-noimport:=true, org.osgi.service.http");
			b.setProperty("Private-Package", "org.osgi.framework, test.refer");
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(new File("bin_test"));
			Jar jar = b.build();
			assertTrue(b.check());

			Manifest m = jar.getManifest();
			String imports = m.getMainAttributes()
				.getValue("Import-Package");
			assertFalse(imports.contains("org.osgi.util.measurement")); // referred
																		// to
																		// but
																		// no
																		// private
																		// references
																		// (does
																		// not
																		// use
																		// fw).
			assertTrue(imports.contains("org.osgi.service.http")); // referred
																	// to
																	// but no
																	// private
																	// references
																	// (does not
																	// use
																	// fw).

		} finally {
			b.close();
		}
	}

	@Test
	public void testAutoNoImport() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("Export-Package",
				"org.osgi.service.event, org.osgi.service.packageadmin, org.osgi.util.measurement, org.osgi.service.http;-noimport:=true");
			b.setProperty("Private-Package", "org.osgi.framework, test.refer");
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(new File("bin_test"));
			Jar jar = b.build();
			assertTrue(b.check("has 1,  private references"));

			Manifest m = jar.getManifest();
			String imports = m.getMainAttributes()
				.getValue("Import-Package");
			assertFalse(imports.contains("org.osgi.service.packageadmin")); // no
																			// internal
																			// references
			assertFalse(imports.contains("org.osgi.util.event")); // refers to
																	// private
																	// framework
			assertTrue(imports.contains("org.osgi.util.measurement")); // referred
																		// to
																		// but
																		// no
																		// private
																		// references
																		// (does
																		// not
																		// use
																		// fw).
			assertFalse(imports.contains("org.osgi.service.http")); // referred
																	// to
																	// but no
																	// private
																	// references
																	// (does not
																	// use
																	// fw).
		} finally {
			b.close();
		}
	}

	@Test
	public void testSimpleWab() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("-wab", "");
			b.setProperty("Private-Package", "org.osgi.service.event");
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			Jar jar = b.build();
			assertTrue(b.check());

			Manifest m = jar.getManifest();
			m.write(System.err);
			assertNotNull(b.getImports()
				.getByFQN("org.osgi.framework"));
		} finally {
			b.close();
		}

	}

	@Test
	public void testWab() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("-wablib", "jar/asm.jar, jar/easymock.jar");
			b.setProperty("-wab", "jar/osgi.jar");
			b.setProperty("-includeresource", "OSGI-INF/xml/x.xml;literal=\"text\"");
			b.setProperty("Private-Package", "org.osgi.framework");
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			Jar jar = b.build();
			assertTrue(b.check());

			Manifest m = jar.getManifest();
			assertNotNull(m);
			assertEquals("WEB-INF/classes,WEB-INF/lib/asm.jar,WEB-INF/lib/easymock.jar", m.getMainAttributes()
				.getValue("Bundle-ClassPath"));
			assertNotNull(jar.getResource("WEB-INF/lib/asm.jar"));
			assertNotNull(jar.getResource("WEB-INF/classes/org/osgi/framework/BundleContext.class"));
			assertNotNull(jar.getResource("osgi.jar"));
			assertNotNull(jar.getResource("OSGI-INF/xml/x.xml"));
		} finally {
			b.close();
		}
	}

	@Test
	public void testRemoveHeaders() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("Private-Package", "org.osgi.framework");
			b.setProperty("T1", "1");
			b.setProperty("T2", "1");
			b.setProperty("T1_2", "1");
			b.setProperty("-removeheaders", "!T1_2,T1*");
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			Jar jar = b.build();
			assertTrue(b.check());

			Manifest m = jar.getManifest();
			assertNotNull(m);
			assertEquals("1", m.getMainAttributes()
				.getValue("T2"));
			assertEquals("1", m.getMainAttributes()
				.getValue("T1_2"));
			assertEquals(null, m.getMainAttributes()
				.getValue("T1"));
		} finally {
			b.close();
		}
	}

	@Test
	public void testNoManifest(@InjectTemporaryDirectory
	File tmp) throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("-nomanifest", "true");
			b.setProperty(Constants.BUNDLE_CLASSPATH, "WEB-INF/classes");
			b.setProperty("Include-Resource", "WEB-INF/classes=@jar/asm.jar");
			Jar jar = b.build();
			assertTrue(b.check());

			File f = IO.getFile(tmp, "tmp.jar");
			jar.write(f);

			JarInputStream jin = new JarInputStream(new FileInputStream(f));
			Manifest m = jin.getManifest();
			assertNull(m);
		} finally {
			b.close();
		}
	}

	@Test
	public void testClassesonNoBCP() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("-resourceonly", "true");
			b.setProperty("Include-Resource", "WEB-INF/classes=@jar/asm.jar");
			b.setProperty("-nomanifest", "true");
			b.build();
			assertTrue(b.check("Classes found in the wrong directory"));
		} finally {
			b.close();
		}
	}

	@Test
	public void testClassesonBCP() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("-resourceonly", "true");
			b.setProperty("Include-Resource", "WEB-INF/classes=@jar/asm.jar");
			b.setProperty("Bundle-ClassPath", "WEB-INF/classes");
			b.build();
			assertTrue(b.check());
		} finally {
			b.close();
		}
	}

	/**
	 * #196 StringIndexOutOfBoundsException in Builder.getClasspathEntrySuffix
	 * If a class path entry was changed the isInScope threw an exception
	 * because it assumed all cpes were directories.
	 *
	 * @throws Exception
	 */
	@Test
	public void testInScopeClasspathEntry() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("Export-Package", "aQute.bnd.*");
			b.addClasspath(new File("bin_test"));
			b.addClasspath(IO.getFile("jar/osgi.jar"));

			List<File> project = Arrays.asList(b.getFile("bin_test/aQute/bnd/build/Project.class"));
			assertTrue(b.isInScope(project));
			List<File> cpe = Arrays.asList(b.getFile("jar/osgi.jar"));
			assertTrue(b.isInScope(cpe));
		} finally {
			b.close();
		}
	}

	@Test
	public void testInScopeExport() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("Export-Package", "aQute.bnd.*");
			b.addClasspath(new File("bin_test"));
			List<File> project = Arrays.asList(b.getFile("bin_test/aQute/bnd/build/Project.class"));
			assertTrue(b.isInScope(project));
			List<File> nonexistent = Arrays.asList(b.getFile("bin_test/aQute/bnd/build/Abc.xyz"));
			assertTrue(b.isInScope(nonexistent));
			List<File> outside = Arrays.asList(b.getFile("bin_test/test/AnalyzerTest.class"));
			assertFalse(b.isInScope(outside));
		} finally {
			b.close();
		}
	}

	@Test
	public void testInScopePrivate() throws Exception {
		Builder b = new Builder();
		b.setProperty("Private-Package", "!aQute.bnd.build,aQute.bnd.*");
		b.addClasspath(new File("bin_test"));
		List<File> project = Arrays.asList(b.getFile("bin_test/aQute/bnd/build/Project.class"));
		assertFalse(b.isInScope(project));
		List<File> nonexistent = Arrays.asList(b.getFile("bin_test/aQute/bnd/acb/Def.xyz"));
		assertTrue(b.isInScope(nonexistent));
		List<File> outside = Arrays.asList(b.getFile("bin_test/test/AnalyzerTest.class"));
		assertFalse(b.isInScope(outside));
	}

	@Test
	public void testInScopeResources() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("Include-Resource",
				"@a.jar/!xya.txt,{@b.jar/!xya.txt}, -@c.jar/!xya.txt, dir, x=dirb, {-x=dirc}");
			assertFalse(b.isInScope(Arrays.asList(b.getFile("x.jar"))));
			assertTrue(b.isInScope(Arrays.asList(b.getFile("a.jar"))));
			assertTrue(b.isInScope(Arrays.asList(b.getFile("b.jar"))));
			assertTrue(b.isInScope(Arrays.asList(b.getFile("dir/a.jar"))));
			assertTrue(b.isInScope(Arrays.asList(b.getFile("dir/x.jar"))));
			assertTrue(b.isInScope(Arrays.asList(b.getFile("dir/x.jar"))));
			assertTrue(b.isInScope(Arrays.asList(b.getFile("dirb/x.jar"))));
			assertTrue(b.isInScope(Arrays.asList(b.getFile("dirb/x.jar"))));
			assertTrue(b.isInScope(Arrays.asList(b.getFile("dirc/x.jar"))));
		} finally {
			b.close();
		}
	}

	@Test
	public void testExtra() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("Include-Resource",
				"jar/osgi.jar;extra=itworks, www/xyz.jar=jar/osgi.jar;extra='italsoworks'");
			b.setProperty("-resourceonly", "true");
			Jar jar = b.build();
			assertTrue(b.check());

			Resource r = jar.getResource("osgi.jar");
			assertNotNull(r);
			assertEquals("itworks", ZipUtil.stringFromExtraField(Resource.decodeExtra(r.getExtra())));
			Resource r2 = jar.getResource("www/xyz.jar");
			assertNotNull(r2);
			assertEquals("italsoworks", ZipUtil.stringFromExtraField(Resource.decodeExtra(r2.getExtra())));
		} finally {
			b.close();
		}
	}

	/**
	 * Got a split package warning during verify when private overlaps with
	 * export
	 */
	@Test
	public void testSplitWhenPrivateOverlapsExport() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty("Private-Package", "org.osgi.service.*");
			b.setProperty("Export-Package", "org.osgi.service.event");
			b.build();
			assertTrue(b.check());
		} finally {
			b.close();
		}
	}

	/**
	 * This test checks if
	 *
	 * @throws Exception
	 */

	@Test
	public void testMacroBasedExpansion() throws Exception {
		Processor proc = new Processor();

		Builder builder = new Builder(proc);
		try {
			builder.setProperty("Export-Package", "${spec.packages}");
			proc.setProperty("spec.packages", "${core.packages}, ${cmpn.packages}, ${mobile.packages}");
			proc.setProperty("core.specs", "org.osgi.service.packageadmin, org.osgi.service.permissionadmin");
			proc.setProperty("core.packages", "${replace;${core.specs};.+;$0.*}");
			proc.setProperty("cmpn.specs", "org.osgi.service.event, org.osgi.service.cu");
			proc.setProperty("cmpn.packages", "${replace;${cmpn.specs};.+;$0.*}");
			proc.setProperty("mobile.specs", "org.osgi.service.wireadmin, org.osgi.service.log, org.osgi.service.cu");
			proc.setProperty("mobile.packages", "${replace;${mobile.specs};.+;$0.*}");
			builder.addClasspath(IO.getFile("jar/osgi.jar"));

			Jar jar = builder.build();
			// The total set is not uniqued so we're having an unused pattern
			// this could be solved with ${uniq;${spec.packages}} but this is
			// just
			// another test
			assertTrue(builder.check("Unused Export-Package instructions: \\[org.osgi.service.cu.\\*~\\]"));
			Domain domain = Domain.domain(jar.getManifest());

			Parameters h = domain.getExportPackage();
			assertTrue(h.containsKey("org.osgi.service.cu"));
			assertTrue(h.containsKey("org.osgi.service.cu.admin"));
		} finally {
			builder.close();
			proc.close();
		}
	}

	/**
	 * Make resolution dependent on the fact that a package is on the classpath
	 * or not
	 */

	@Test
	public void testConditionalResolution() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty("res", "${if;${exporters;${@package}};mandatory;optional}");
			b.setProperty("Import-Package", "*;resolution:=\\${res}");
			b.setProperty("Export-Package", "org.osgi.service.io, org.osgi.service.log");
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.build();
			assertTrue(b.check());

			Map<String, String> ioimports = b.getImports()
				.getByFQN("javax.microedition.io");
			Map<String, String> fwimports = b.getImports()
				.getByFQN("org.osgi.framework");

			assertNotNull(ioimports);
			assertNotNull(fwimports);
			assertTrue(ioimports.containsKey("resolution:"));
			assertTrue(fwimports.containsKey("resolution:"));
			assertEquals("optional", ioimports.get("resolution:"));
			assertEquals("mandatory", fwimports.get("resolution:"));
		} finally {
			b.close();
		}

	}

	/**
	 * Test private imports. We first build a jar with a import:=private packge.
	 * Then place it
	 *
	 * @throws Exception
	 */

	@Test
	public void testClassnames() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.addClasspath(IO.getFile("jar/ds.jar"));
			b.addClasspath(IO.getFile("jar/ifc112.jar"));
			b.setProperty("Export-Package", "*");
			b.setProperty("C1", "${classes;implementing;org.osgi.service.component.*}");
			b.setProperty("C2", "${classes;extending;org.xml.sax.helpers.*}");
			b.setProperty("C3", "${classes;importing;org.xml.sax}");
			b.setProperty("C4", "${classes;named;*Parser*}");
			b.setProperty("C5", "${classes;named;*Parser*;version;45.*}");
			Jar jar = b.build();
			assertTrue(b.check());

			Manifest m = jar.getManifest();
			m.write(System.err);
			Attributes main = m.getMainAttributes();
			assertList(asl(
				"org.eclipse.equinox.ds.service.ComponentContextImpl,org.eclipse.equinox.ds.service.ComponentFactoryImpl,org.eclipse.equinox.ds.service.ComponentInstanceImpl"),
				asl(main.getValue("C1")));
			assertList(asl("org.eclipse.equinox.ds.parser.ElementHandler, "
				+ "org.eclipse.equinox.ds.parser.IgnoredElement,"
				+ "org.eclipse.equinox.ds.parser.ImplementationElement,"
				+ "org.eclipse.equinox.ds.parser.ParserHandler, " + "org.eclipse.equinox.ds.parser.PropertiesElement,"
				+ "org.eclipse.equinox.ds.parser.PropertyElement, " + "org.eclipse.equinox.ds.parser.ProvideElement, "
				+ "org.eclipse.equinox.ds.parser.ReferenceElement, " + "org.eclipse.equinox.ds.parser.ServiceElement,"
				+ "org.eclipse.equinox.ds.parser.ComponentElement"), asl(main.getValue("C2")));
			assertList(asl(
				"org.eclipse.equinox.ds.parser.ComponentElement,org.eclipse.equinox.ds.parser.ElementHandler,org.eclipse.equinox.ds.parser.IgnoredElement,org.eclipse.equinox.ds.parser.ImplementationElement,org.eclipse.equinox.ds.parser.Parser,org.eclipse.equinox.ds.parser.ParserHandler,org.eclipse.equinox.ds.parser.PropertiesElement,org.eclipse.equinox.ds.parser.PropertyElement,org.eclipse.equinox.ds.parser.ProvideElement,org.eclipse.equinox.ds.parser.ReferenceElement,org.eclipse.equinox.ds.parser.ServiceElement"),
				asl(main.getValue("C3")));
			assertList(asl(
				"org.eclipse.equinox.ds.parser.XMLParserNotAvailableException,org.eclipse.equinox.ds.parser.Parser,org.eclipse.equinox.ds.parser.ParserHandler,netscape.application.HTMLParser,org.eclipse.equinox.ds.parser.ParserConstants,org.osgi.util.xml.XMLParserActivator"),
				asl(main.getValue("C4")));
			assertEquals("netscape.application.HTMLParser", main.getValue("C5"));
		} finally {
			b.close();
		}
	}

	static void assertList(Collection<String> a, Collection<String> b) {
		List<String> onlyInA = new ArrayList<>();
		onlyInA.addAll(a);
		onlyInA.removeAll(b);

		List<String> onlyInB = new ArrayList<>();
		onlyInB.addAll(b);
		onlyInB.removeAll(a);

		if (onlyInA.isEmpty() && onlyInB.isEmpty())
			return;

		fail("Lists are not equal, only in A: " + onlyInA + ",\n   and only in B: " + onlyInB);
	}

	static Collection<String> asl(String s) {
		return new TreeSet<>(Processor.split(s));
	}

	@Test
	public void testImportMicroNotTruncated() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty("Import-Package", "org.osgi.service.event;version=${@}");
			b.build();
			assertTrue(b.check("The JAR is empty"));
			String s = b.getImports()
				.getByFQN("org.osgi.service.event")
				.get("version");
			assertEquals("1.0.1", s);
		} finally {
			b.close();
		}
	}

	@Test
	public void testImportMicroTruncated() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/osgi.jar"));
			b.setProperty("Import-Package", "org.osgi.service.event");
			b.build();
			assertTrue(b.check("The JAR is empty"));

			String s = b.getImports()
				.getByFQN("org.osgi.service.event")
				.get("version");
			assertEquals("[1.0,2)", s);
		} finally {
			b.close();
		}

	}

	@Test
	public void testMultipleExport2() throws Exception {
		File cp[] = {
			IO.getFile("jar/asm.jar")
		};
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.setProperty("Export-Package",
				"org.objectweb.asm;version=1.1, org.objectweb.asm;version=1.2, org.objectweb.asm;version=2.3");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			jar.getManifest()
				.write(System.err);
			Manifest m = jar.getManifest();
			m.write(System.err);
			String ip = m.getMainAttributes()
				.getValue("Export-Package");
			assertTrue(ip.contains("org.objectweb.asm;version=\"1.1\""));
			assertTrue(ip.contains("org.objectweb.asm;version=\"1.2\""));
			assertTrue(ip.contains("org.objectweb.asm;version=\"2.3\""));
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testBsnAssignmentNoFile() throws Exception {
		Properties p = new Properties();
		p.setProperty("Private-Package", "org.objectweb.asm");
		Attributes m = setup(p, null).getMainAttributes();

		// We use properties so the default BSN is then the project name
		// because that is the base directory
		assertEquals(m.getValue("Bundle-SymbolicName"), "biz.aQute.bndlib.tests");

		// The file name for the properties is not bnd.bnd, so the
		// name of the properties file is the default bsn
		m = setup(null, IO.getFile("test/test/com.acme/defaultbsn.bnd")).getMainAttributes();
		assertEquals("com.acme.defaultbsn", m.getValue("Bundle-SymbolicName"));

		// If the file is called bnd.bnd, then we take the parent directory
		m = setup(null, IO.getFile("test/test/com.acme/bnd.bnd")).getMainAttributes();
		assertEquals("com.acme", m.getValue("Bundle-SymbolicName"));

		// If the file is called bnd.bnd, then we take the parent directory
		m = setup(null, IO.getFile("test/test/com.acme/setsbsn.bnd")).getMainAttributes();
		assertEquals("is.a.set.bsn", m.getValue("Bundle-SymbolicName"));

		// This sets the bsn, se we should see it back
		p.setProperty("Bundle-SymbolicName", "this.is.my.test");
		m = setup(p, null).getMainAttributes();
		assertEquals(m.getValue("Bundle-SymbolicName"), "this.is.my.test");
	}

	public static Manifest setup(Properties p, File f) throws Exception {
		File cp[] = {
			IO.getFile("jar/asm.jar")
		};
		Builder bmaker = new Builder();
		if (f != null)
			bmaker.setProperties(f);
		else
			bmaker.setProperties(p);
		bmaker.setClasspath(cp);
		Jar jar = bmaker.build();
		assertTrue(bmaker.check());
		Manifest m = jar.getManifest();
		return m;
	}

	@Test
	public void testDuplicateExport() throws Exception {
		File cp[] = {
			IO.getFile("jar/asm.jar")
		};
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.setProperty("Import-Package", "*");
			p.setProperty("Export-Package", "org.*;version=1.2,org.objectweb.asm;version=1.3");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			Manifest m = jar.getManifest();
			m.write(System.err);
			String ip = m.getMainAttributes()
				.getValue("Export-Package");
			assertTrue(ip.contains("org.objectweb.asm;version=\"1.2\""));
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testNoExport() throws Exception {
		File cp[] = {
			IO.getFile("jar/asm.jar")
		};
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.setProperty("Import-Package", "*");
			p.setProperty("Export-Package", "org.*");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			jar.getManifest()
				.write(System.err);
			Manifest m = jar.getManifest();
			String ip = m.getMainAttributes()
				.getValue("Export-Package");
			assertTrue(ip.contains("org.objectweb.asm"));
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testHardcodedImport() throws Exception {
		File cp[] = {
			IO.getFile("jar/asm.jar")
		};
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.setProperty("Import-Package", "whatever,*");
			p.setProperty("Export-Package", "org.*");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			Manifest m = jar.getManifest();
			String ip = m.getMainAttributes()
				.getValue("Import-Package");
			assertTrue(ip.contains("whatever"));
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testCopyDirectory() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.setProperty("-resourceonly", "true");
			p.setProperty("Include-Resource", "bnd=bnd");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			Map<String, Resource> map = jar.getDirectories()
				.get("bnd");
			assertNotNull(map);
			try (Stream<Path> paths = Files.find(Paths.get("bnd"), Integer.MAX_VALUE, (t, a) -> a.isRegularFile(),
				FileVisitOption.FOLLOW_LINKS)) {
				assertEquals(paths.count(), map.size());
			}
		} finally {
			bmaker.close();
		}
	}

	/**
	 * There is an error that gives a split package when you export a package
	 * that is also private I think.
	 *
	 * @throws Exception
	 */
	@Test
	public void testSplitOnExportAndPrivate() throws Exception {
		File cp[] = {
			IO.getFile("jar/asm.jar")
		};
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.setProperty("Export-Package", "org.objectweb.asm.signature");
			p.setProperty("Private-Package", "org.objectweb.asm");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			bmaker.build();
			assertTrue(bmaker.check());
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testConduit() throws Exception {
		Properties p = new Properties();
		p.setProperty("-conduit", "jar/asm.jar");
		Builder b = new Builder();
		try {
			b.setProperties(p);
			Jar jars[] = b.builds();
			assertTrue(b.check());
			assertNotNull(jars);
			assertEquals(1, jars.length);
			assertEquals("ASM", jars[0].getManifest()
				.getMainAttributes()
				.getValue("Implementation-Title"));
		} finally {
			b.close();
		}
	}

	@Test
	public void testSignedJarConduit(@InjectTemporaryDirectory
	File tmp) throws Exception {
		Properties p = new Properties();
		p.setProperty("-conduit", "jar/osgi-3.0.0.jar");
		try (Builder b = new Builder()) {
			b.setProperties(p);
			Jar jars[] = b.builds();
			assertTrue(b.check());
			assertNotNull(jars);
			assertEquals(1, jars.length);

			Jar jar = jars[0];
			Resource r = jar.getResource("META-INF/OSGI.RSA");
			assertNotNull(r);

			File f = IO.getFile(tmp, "tmp.jar");
			jar.write(f);

			try (Jar wj = new Jar(f)) {
				Resource wr = wj.getResource("META-INF/OSGI.RSA");
				assertNotNull(wr);
				assertEquals(wj.getSHA256(), jar.getSHA256());
			}
		}
	}

	/**
	 * Export a package that was loaded with resources
	 *
	 * @throws Exception
	 */
	@Test
	public void testExportSyntheticPackage() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.setProperty("-resourceonly", "true");
			p.setProperty("Include-Resource", "resources=jar");
			p.setProperty("-exportcontents", "resources");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			Manifest manifest = jar.getManifest();
			String header = manifest.getMainAttributes()
				.getValue("Export-Package");
			System.err.println(header);
			assertTrue(header.contains("resources"));
		} finally {
			bmaker.close();
		}
	}

	/**
	 * Exporting packages in META-INF
	 *
	 * @throws Exception
	 */
	@Test
	public void testMETAINF() throws Exception {
		File cp[] = {
			new File("test"), IO.getFile("jar/asm.jar")
		};
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.setProperty("Include-Resource", "META-INF/xyz/asm.jar=jar/asm.jar");
			p.setProperty("Export-Package", "META-INF/xyz, org.*");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check("Invalid package name: 'META-INF"));

			jar.getManifest()
				.write(System.err);
			Manifest manifest = jar.getManifest();
			String header = manifest.getMainAttributes()
				.getValue("Export-Package");
			assertTrue(header.contains("META-INF.xyz"));
		} finally {
			bmaker.close();
		}
	}

	/**
	 * Bnd cleans up versions if they do not follow the OSGi rule. Check a
	 * number of those versions.
	 *
	 * @throws Exception
	 */
	@Test
	public void testVersionCleanup() throws Exception {
		assertVersion("1.201209072340200", "1.0.0.201209072340200");
		assertVersion("000001.0003.00000-SNAPSHOT", "1.3.0.SNAPSHOT");
		assertVersion("000000.0000.00000-SNAPSHOT", "0.0.0.SNAPSHOT");
		assertVersion("0-SNAPSHOT", "0.0.0.SNAPSHOT");
		assertVersion("1.3.0.0-0-01-0-SNAPSHOT", "1.3.0.0-0-01-0-SNAPSHOT");
		assertVersion("1.3.0.0-0-01-0", "1.3.0.0-0-01-0");
		assertVersion("0.9.0.1.2.3.4.5-incubator-SNAPSHOT", "0.9.0.incubator-SNAPSHOT");
		assertVersion("0.4aug123", "0.0.0.4aug123");
		assertVersion("0.9.4aug123", "0.9.0.4aug123");
		assertVersion("0.9.0.4aug123", "0.9.0.4aug123");

		assertVersion("1.2.3", "1.2.3");
		assertVersion("1.2.3-123", "1.2.3.123");
		assertVersion("1.2.3.123", "1.2.3.123");
		assertVersion("1.2.3.123x", "1.2.3.123x");
		assertVersion("1.123x", "1.0.0.123x");

		assertVersion("0.9.0.4.3.2.1.0.4aug123", "0.9.0.4aug123");
		assertVersion("0.9.0.4aug123", "0.9.0.4aug123");

		assertVersion("0.9.0.4.3.4.5.6.6", "0.9.0.6");

		assertVersion("0.9.0-incubator-SNAPSHOT", "0.9.0.incubator-SNAPSHOT");
		assertVersion("1.2.3.x", "1.2.3.x");
		assertVersion("1.2.3", "1.2.3");
		assertVersion("1.2", "1.2");
		assertVersion("1", "1");
		assertVersion("1.2.x", "1.2.0.x");
		assertVersion("1.x", "1.0.0.x");
		assertVersion("1.2.3-x", "1.2.3.x");
		assertVersion("1.2:x", "1.2.0.x");
		assertVersion("1.2-snapshot", "1.2.0.snapshot");
		assertVersion("1#x", "1.0.0.x");
		assertVersion("1.&^%$#date2007/03/04", "1.0.0.date20070304");
	}

	static void assertVersion(String input, String expected) {
		assertEquals(expected, Analyzer.cleanupVersion(input));
	}

	/**
	 * -exportcontents provides a header that is only relevant in the analyze
	 * phase, it augments the Export-Package header.
	 */

	@Test
	public void testExportContents() throws Exception {
		Builder builder = new Builder();
		try {
			builder.setProperty(Constants.INCLUDERESOURCE, "test/activator/inherits=test/test/activator/inherits");
			builder.setProperty("-exportcontents", "*;x=true;version=1");
			builder.build();
			assertTrue(builder.check());
			Manifest manifest = builder.calcManifest();
			Attributes main = manifest.getMainAttributes();
			Parameters map = OSGiHeader.parseHeader(main.getValue("Export-Package"));
			Map<String, String> export = map.get("test.activator.inherits");
			assertNotNull(export);
			assertEquals("1", export.get("version"));
			assertEquals("true", export.get("x"));
		} finally {
			builder.close();
		}
	}

	/**
	 * Check Conditional package. First import a subpackage then let the
	 * subpackage import a super package. This went wrong in the OSGi build. We
	 * see such a pattern in the Spring jar. The package
	 * org.springframework.beans.factory.access refers to
	 * org.springframework.beans.factory and org.springframework.beans. The
	 */
	@Test
	public void testConditionalBaseSuper() throws Exception {
		Builder b = new Builder();
		try {
			b.setProperty(Constants.CONDITIONALPACKAGE, "test.top.*");
			b.setProperty(Constants.PRIVATEPACKAGE, "test.top.middle.bottom");
			b.addClasspath(new File("bin_test"));
			Jar dot = b.build();
			assertTrue(b.check());

			assertNotNull(dot.getResource("test/top/middle/bottom/Bottom.class"));
			assertNotNull(dot.getResource("test/top/middle/Middle.class"));
			assertNotNull(dot.getResource("test/top/Top.class"));

			assertFalse(b.getImports()
				.getByFQN("test.top") != null);
			assertFalse(b.getImports()
				.getByFQN("test.top.middle") != null);
			assertFalse(b.getImports()
				.getByFQN("test.top.middle.bottom") != null);
		} finally {
			b.close();
		}
	}

	/**
	 * It looks like Conditional-Package can add the same package multiple
	 * times. So lets test this.
	 */
	@Test
	public void testConditional2() throws Exception {
		Properties base = new Properties();
		base.put(Constants.EXPORT_PACKAGE, "org.osgi.service.log");
		base.put(Constants.CONDITIONAL_PACKAGE, "org.osgi.*");
		Builder analyzer = new Builder();
		try {
			analyzer.setProperties(base);
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/osgi.jar")
			});
			analyzer.build();
			assertTrue(analyzer.check("private references"));
			Jar jar = analyzer.getJar();
			assertTrue(analyzer.check());
			assertNotNull(analyzer.getExports()
				.getByFQN("org.osgi.service.log"));
			assertNotNull(jar.getDirectories()
				.get("org/osgi/framework"));
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Test the strategy: error
	 */
	@Test
	public void testStrategyError() throws Exception {
		Properties base = new Properties();
		base.put(Constants.EXPORT_PACKAGE, "*;-split-package:=error");
		Builder analyzer = new Builder();
		try {
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/asm.jar"), IO.getFile("jar/asm.jar")
			});
			analyzer.setProperties(base);
			analyzer.build();
			assertTrue(analyzer.check("The JAR is empty", "Split package"));
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Test the strategy: default
	 */
	@Test
	public void testStrategyDefault() throws Exception {
		Properties base = new Properties();
		base.put(Constants.EXPORT_PACKAGE, "*");
		Builder analyzer = new Builder();
		try {
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/asm.jar"), IO.getFile("jar/asm.jar")
			});
			analyzer.setProperties(base);
			analyzer.build();
			assertEquals(2, analyzer.getWarnings()
				.size());
			assertTrue(analyzer.check("Split package"));
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Test the strategy: merge-first
	 */
	@Test
	public void testStrategyMergeFirst() throws Exception {
		Properties base = new Properties();
		base.put(Constants.EXPORT_PACKAGE, "*;-split-package:=merge-first");
		Builder analyzer = new Builder();
		try {
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/asm.jar"), IO.getFile("jar/asm.jar")
			});
			analyzer.setProperties(base);
			analyzer.build();
			assertTrue(analyzer.check());
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Test the strategy: merge-last
	 */
	@Test
	public void testStrategyMergeLast() throws Exception {
		Properties base = new Properties();
		base.put(Constants.EXPORT_PACKAGE, "*;-split-package:=merge-last");
		Builder analyzer = new Builder();
		try {
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/asm.jar"), IO.getFile("jar/asm.jar")
			});
			analyzer.setProperties(base);
			analyzer.build();
			assertTrue(analyzer.check());
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Test Resource inclusion that do not exist
	 *
	 * @throws Exception
	 */
	@Test
	public void testResourceNotFound() throws Exception {
		Properties base = new Properties();
		base.put(Constants.EXPORT_PACKAGE, "*;x-test:=true");
		base.put(Constants.INCLUDERESOURCE, "does_not_exist");
		Builder analyzer = new Builder();
		try {
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/asm.jar")
			});
			analyzer.setProperties(base);
			analyzer.build();
			assertTrue(analyzer.check("file does not exist: does_not_exist"));
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Check if we can use findpath to build the Bundle-Classpath.
	 */

	@Test
	public void testFindPathInBundleClasspath() throws Exception {
		Properties base = new Properties();
		base.put(Constants.INCLUDERESOURCE, "jar=jar");
		base.put(Constants.BUNDLE_CLASSPATH, "${findpath;jar/.{1,4}\\.jar}");
		Builder analyzer = new Builder();
		try {
			analyzer.setProperties(base);
			analyzer.build();
			assertTrue(analyzer.check());

			Manifest manifest = analyzer.getJar()
				.getManifest();
			String bcp = manifest.getMainAttributes()
				.getValue("Bundle-Classpath");

			assertTrue(bcp.contains("ds.jar"));
			assertTrue(bcp.contains("asm.jar"));
			assertTrue(bcp.contains("bcel.jar"));
			assertTrue(bcp.contains("mina.jar"));
			assertTrue(bcp.contains("rox.jar"));
			assertTrue(bcp.contains("osgi.jar"));
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Check if we export META-INF when we export the complete classpath.
	 */

	@Test
	public void testVersionCleanupAll() throws Exception {
		Properties base = new Properties();
		base.put(Constants.EXPORT_PACKAGE, "*");
		base.put(Constants.BUNDLE_VERSION, "0.9.0-incubator-SNAPSHOT");
		Builder analyzer = new Builder();
		try {
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/asm.jar")
			});
			analyzer.setProperties(base);
			analyzer.build();

			assertTrue(analyzer.check());
			Manifest manifest = analyzer.getJar()
				.getManifest();
			String version = manifest.getMainAttributes()
				.getValue(Constants.BUNDLE_VERSION);
			assertEquals("0.9.0.incubator-SNAPSHOT", version);
		} finally {
			analyzer.close();
		}
	}

	/**
	 * We are only adding privately the core equinox ds package. We then add
	 * conditionally all packages that should belong to this as well as any OSGi
	 * interfaces.
	 *
	 * @throws Exception
	 */
	@Test
	public void testConditional() throws Exception {
		File cp[] = {
			IO.getFile("jar/osgi.jar"), IO.getFile("jar/ds.jar"), IO.getFile("bin_test")
		};
		try (Builder bmaker = new Builder()) {
			Properties p = new Properties();
			p.put("Import-Package", "*");
			p.put("Private-Package", "org.eclipse.equinox.ds,test.api");
			p.put("Conditional-Package", "org.eclipse.equinox.ds.*, org.osgi.service.*");
			p.put("-exportcontents", "${removeall;${packages;versioned};${packages;conditional}}");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			bmaker.setProperty("Conditional", "${packages;conditional}");
			bmaker.build();
			assertTrue(bmaker.check());

			assertTrue(bmaker.getContained()
				.getByFQN("org.eclipse.equinox.ds.instance") != null);
			assertTrue(bmaker.getContained()
				.getByFQN("org.eclipse.equinox.ds.model") != null);
			assertTrue(bmaker.getContained()
				.getByFQN("org.eclipse.equinox.ds.parser") != null);
			assertTrue(bmaker.getContained()
				.getByFQN("org.osgi.service.cm") != null);
			assertTrue(bmaker.getContained()
				.getByFQN("org.osgi.service.component") != null);
			assertFalse(bmaker.getContained()
				.getByFQN("org.osgi.service.wireadmin") != null);
			Parameters exported = new Parameters(bmaker.getJar()
				.getManifest()
				.getMainAttributes()
				.getValue("Export-Package"));
			assertTrue(exported.containsKey("test.api"));
			assertEquals(1, exported.size());
			Parameters conditional = new Parameters(bmaker.getProperty("Conditional"));
			assertFalse(conditional.containsKey("org.eclipse.equinox.ds"));
			assertTrue(conditional.containsKey("org.eclipse.equinox.ds.model"));
			assertTrue(conditional.containsKey("org.eclipse.equinox.ds.parser"));
			assertTrue(conditional.containsKey("org.eclipse.equinox.ds.resolver"));
			assertTrue(conditional.containsKey("org.eclipse.equinox.ds.tracker"));
			assertTrue(conditional.containsKey("org.eclipse.equinox.ds.workqueue"));
			assertTrue(conditional.containsKey("org.eclipse.equinox.ds.instance"));
			assertTrue(conditional.containsKey("org.eclipse.equinox.ds.service"));
			assertTrue(conditional.containsKey("org.osgi.service.log"));
			assertTrue(conditional.containsKey("org.osgi.service.packageadmin"));
			assertTrue(conditional.containsKey("org.osgi.service.cm"));
			assertTrue(conditional.containsKey("org.osgi.service.component"));
			assertFalse(conditional.containsKey("org.osgi.service.wireadmin"));
		}
	}

	/**
	 * Check if we export META-INF when we export the complete classpath.
	 */

	@Test
	public void testMetaInfExport() throws Exception {
		Properties base = new Properties();
		base.put(Constants.EXPORT_PACKAGE, "*");
		Builder analyzer = new Builder();
		try {
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/asm.jar")
			});
			analyzer.setProperties(base);
			analyzer.build();
			assertTrue(analyzer.check());
			assertFalse(analyzer.getExports()
				.getByFQN("META-INF") != null);
			assertTrue(analyzer.getExports()
				.getByFQN("org.objectweb.asm") != null);
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Check if we imported the package with the correct version range when
	 * there's an empty package in front of it in the classpath. First form.
	 */

	@Test
	public void testImportRangeCalculatedFromClasspath_1() throws Exception {
		Properties base = new Properties();
		base.put(Constants.IMPORT_PACKAGE, "javax.servlet,javax.servlet.http");

		Builder analyzer = new Builder();
		try {
			analyzer.addClasspath(new File("bin_test"));
			analyzer.setPrivatePackage("test");
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/jsp-api.jar"), IO.getFile("jar/servlet-api.jar")
			});
			analyzer.setProperties(base);
			analyzer.build();
			assertTrue(analyzer.check());

			Packages imports = analyzer.getImports();
			Attrs attrs = imports.getByFQN("javax.servlet.http");
			assertEquals("[3.0,4)", attrs.getVersion());
			attrs = imports.getByFQN("javax.servlet");
			assertEquals("[3.0,4)", attrs.getVersion());
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Check if we imported the package with the correct version range when
	 * there's an empty package in front of it in the classpath. Second form.
	 */

	@Test
	public void testImportRangeCalculatedFromClasspath_2() throws Exception {
		Properties base = new Properties();
		base.put(Constants.IMPORT_PACKAGE, "javax.servlet,javax.servlet.http");

		base.put("pwd", IO.work.toURI()
			.toString());
		base.put("-classpath", "${pwd}/jar/jsp-api.jar,${pwd}/jar/servlet-api.jar");

		Builder analyzer = new Builder();
		try {
			analyzer.addClasspath(new File("bin_test"));
			analyzer.setPrivatePackage("test");
			analyzer.setProperties(base);
			analyzer.build();
			assertTrue(analyzer.check());

			Packages imports = analyzer.getImports();
			Attrs attrs = imports.getByFQN("javax.servlet.http");
			assertEquals("[3.0,4)", attrs.getVersion());
			attrs = imports.getByFQN("javax.servlet");
			assertEquals("[3.0,4)", attrs.getVersion());
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Check if we imported the package with the correct version range when
	 * there's an empty package in front of it in the classpath. First form
	 * calling builds().
	 */

	@Test
	public void testImportRangeCalculatedFromClasspath_3() throws Exception {
		Properties base = new Properties();
		base.put(Constants.IMPORT_PACKAGE, "javax.servlet,javax.servlet.http");

		Builder analyzer = new Builder();
		try {
			analyzer.addClasspath(new File("bin_test"));
			analyzer.setPrivatePackage("test");
			analyzer.setClasspath(new File[] {
				IO.getFile("jar/jsp-api.jar"), IO.getFile("jar/servlet-api.jar")
			});
			analyzer.setProperties(base);
			analyzer.builds();
			assertTrue(analyzer.check());

			Packages imports = analyzer.getImports();
			Attrs attrs = imports.getByFQN("javax.servlet.http");
			assertEquals("[3.0,4)", attrs.getVersion());
			attrs = imports.getByFQN("javax.servlet");
			assertEquals("[3.0,4)", attrs.getVersion());
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Check if we imported the package with the correct version range when
	 * there's an empty package in front of it in the classpath. Second form
	 * calling builds().
	 */

	@Test
	public void testImportRangeCalculatedFromClasspath_4() throws Exception {
		Properties base = new Properties();
		base.put(Constants.IMPORT_PACKAGE, "javax.servlet,javax.servlet.http");

		base.put("pwd", IO.work.toURI()
			.toString());
		base.put("-classpath", "${pwd}/jar/jsp-api.jar,${pwd}/jar/servlet-api.jar");

		Builder analyzer = new Builder();
		try {
			analyzer.addClasspath(new File("bin_test"));
			analyzer.setPrivatePackage("test");
			analyzer.setProperties(base);
			analyzer.builds();
			assertTrue(analyzer.check());

			Packages imports = analyzer.getImports();
			Attrs attrs = imports.getByFQN("javax.servlet.http");
			assertEquals("[3.0,4)", attrs.getVersion());
			attrs = imports.getByFQN("javax.servlet");
			assertEquals("[3.0,4)", attrs.getVersion());
		} finally {
			analyzer.close();
		}
	}

	/**
	 * Check that the activator is found.
	 *
	 * @throws Exception
	 */
	@Test
	public void testFindActivator() throws Exception {
		Builder bmaker = new Builder();
		try {
			bmaker.setProperty("Bundle-Activator", "test.activator.Activator");
			bmaker.setProperty("build", "xyz"); // for @Version annotation
			bmaker.setProperty("Private-Package", "test.*");
			bmaker.setProperty("-bundleannotations",
				"!test.annotationheaders.attrs.std.activator.TypeInVersionedPackage,*");
			bmaker.setProperty("-dsannotations", "!*");
			bmaker.setProperty("-metatypeannotations", "!*");
			bmaker.setClasspath(new File[] {
				new File("bin_test")
			});
			bmaker.setProperty("-fixupmessages.export",
				"The annotation aQute.bnd.annotation.Export applied to package test.versionpolicy.api is deprecated and will be removed in a future release. The org.osgi.annotation.bundle.Export should be used instead");
			bmaker.setProperty("-fixupmessages.directive",
				"Unknown directive foobar: in Export-Package, allowed directives are uses:,mandatory:,include:,exclude:,-import:, and 'x-*'");

			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			report("testFindActivator", bmaker, jar);
			assertEquals(0, bmaker.getErrors()
				.size());
			assertEquals(0, bmaker.getWarnings()
				.size());
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testImportVersionRange() throws Exception {
		assertVersionEquals("[1.1,2.0)", "[1.1,2.0)");
		assertVersionEquals("[${@},2.0)", "[1.3,2.0)");
		assertVersionEquals("[${@},${@}]", "[1.3,1.3]");
	}

	static void assertVersionEquals(String input, String output) throws Exception {
		File cp[] = {
			IO.getFile("jar/osgi.jar")
		};
		Builder bmaker = new Builder();
		try {
			bmaker.setClasspath(cp);
			Properties p = new Properties();
			p.put(Constants.EXPORT_PACKAGE, "test.activator");
			p.put(Constants.IMPORT_PACKAGE, "org.osgi.framework;version=\"" + input + "\"");
			bmaker.setProperties(p);
			bmaker.build();
			assertTrue(bmaker.check("The JAR is empty"));
			Packages imports = bmaker.getImports();
			Map<String, String> framework = imports.get(bmaker.getPackageRef("org.osgi.framework"));
			assertEquals(output, framework.get("version"));
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testImportExportBadVersion() throws Exception {
		Builder b = new Builder();
		try {
			b.addClasspath(IO.getFile("jar/ds.jar"));
			b.setProperty(Constants.BUNDLE_VERSION, "0.9.5-@#SNAPSHOT");
			b.setProperty(Constants.EXPORT_PACKAGE, "*;version=0.9.5-@#SNAPSHOT");
			b.setProperty(Constants.IMPORT_PACKAGE, "*;version=0.9.5-@#SNAPSHOT");

			Jar jar = b.build();
			assertTrue(b.check());
			Manifest m = jar.getManifest();
			m.write(System.err);
			assertEquals(m.getMainAttributes()
				.getValue("Bundle-Version"), "0.9.5.SNAPSHOT");

			assertNotNull(b.getExports()
				.getByFQN("org.eclipse.equinox.ds.parser"));
			assertEquals("0.9.5.SNAPSHOT", b.getExports()
				.getByFQN("org.eclipse.equinox.ds.parser")
				.getVersion());

			assertNotNull(b.getImports()
				.getByFQN("org.osgi.framework"));
			assertEquals("0.9.5.SNAPSHOT", b.getImports()
				.getByFQN("org.osgi.framework")
				.getVersion());
		} finally {
			b.close();
		}
	}

	/**
	 * Check imports discovered from bundle classpath.
	 *
	 * @throws Exception
	 */
	@Test
	public void testBundleClasspath4() throws Exception {
		try (Builder builder = new Builder()) {
			Properties p = new Properties();
			p.put("-includeresource", "jar/cxf-rt-rs-sse-3.2.5.jar;lib:=true");
			p.put("Export-Package", "test.referApi");
			builder.setProperties(p);
			builder.setClasspath(new File[] {
				new File("bin_test")
			});
			Jar jar = builder.build();
			assertTrue(builder.check());

			report("testBundleClasspath3", builder, jar);
			assertEquals(0, builder.getErrors()
				.size());
			assertEquals(0, builder.getWarnings()
				.size());

			Domain domain = Domain.domain(jar.getManifest());

			assertEquals(".,cxf-rt-rs-sse-3.2.5.jar", domain.getBundleClasspath()
				.toString());
			assertNotNull(jar.getResource("cxf-rt-rs-sse-3.2.5.jar"));

			Parameters importPackage = domain.getImportPackage();

			Domain cxfrtrssse = Domain.domain(IO.getFile("jar/cxf-rt-rs-sse-3.2.5.jar"));

			SoftAssertions softly = new SoftAssertions();

			cxfrtrssse.getImportPackage()
				.stream()
				.forEach((pkg, attrs) -> {
					// Get the resolution on the imported package on the
					// embedded jar
					Optional.ofNullable(attrs.get(RESOLUTION_DIRECTIVE))
						.ifPresent(
							// Check that it matches the (default) resolution
							// that BND calculated
							expected -> {
								Attrs calculatedAttrs = importPackage.get(pkg);
								softly.assertThat(calculatedAttrs.get(RESOLUTION_DIRECTIVE))
									.as(() -> "resolution on package " + pkg)
									.isEqualTo(expected);
							});
			});

			softly.assertAll();
		}
	}

	/**
	 * Check if can find an activator in the bundle while using a complex bundle
	 * classpath.
	 *
	 * @throws Exception
	 */
	@Test
	public void testBundleClasspath3() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put("Export-Package", "test.activator;-split-package:=merge-first");
			p.put("Bundle-Activator", "test.activator.Activator");
			p.put("Import-Package", "*");
			p.put("Include-Resource", "ds.jar=jar/ds.jar");
			p.put("Bundle-ClassPath", ".,ds.jar");
			bmaker.setProperties(p);
			bmaker.setClasspath(new File[] {
				new File("bin_test"), new File("test")
			});
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			report("testBundleClasspath3", bmaker, jar);
			assertEquals(0, bmaker.getErrors()
				.size());
			assertEquals(0, bmaker.getWarnings()
				.size());
		} finally {
			bmaker.close();
		}

	}

	/**
	 * Check if can find an activator in a embedded jar while using a complex
	 * bundle classpath.
	 *
	 * @throws Exception
	 */
	@Test
	public void testBundleClasspath2() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put("Bundle-Activator", "org.eclipse.equinox.ds.Activator");
			p.put("Private-Package", "test.activator;-split-package:=merge-first");
			p.put("Import-Package", "*");
			p.put("Include-Resource", "ds.jar=jar/ds.jar");
			p.put("Bundle-ClassPath", ".,ds.jar");
			bmaker.setProperties(p);
			bmaker.setClasspath(new File[] {
				new File("bin_test"), new File("test")
			});
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			report("testBundleClasspath2", bmaker, jar);
			assertEquals(bmaker.getErrors()
				.size(), 0);
			assertEquals(bmaker.getWarnings()
				.size(), 0);
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testBundleClasspath() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put("Export-Package", "test.activator;-split-package:=merge-first");
			p.put("Bundle-Activator", "test.activator.Activator");
			p.put("Import-Package", "*");
			p.put("Bundle-ClassPath", ".");
			bmaker.setProperties(p);
			bmaker.setClasspath(new File[] {
				new File("bin_test"), new File("test")
			});
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			report("testBundleClasspath", bmaker, jar);
			jar.exists("testresources/activator/Activator.class");
			assertEquals(bmaker.getErrors()
				.size(), 0);
			assertEquals(bmaker.getWarnings()
				.size(), 0);
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testUnreferredImport() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();

			p.put("-classpath", "jar/mina.jar");
			p.put("Export-Package", "*");
			p.put("Import-Package", "org.apache.commons.collections.map,*");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			report("testUnreferredImport", bmaker, jar);
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testUnreferredNegatedImport() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();

			p.put("-classpath", "jar/mina.jar");
			p.put("Export-Package", "*");
			p.put("Import-Package", "!org.apache.commons.collections.map,*");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			report("testUnreferredImport", bmaker, jar);
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testIncludeResourceResourcesOnlyJar2() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();

			p.put("-classpath", "jar/ro.jar");
			p.put("Export-Package", "*");
			p.put("Import-Package", "");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			report("testIncludeResourceResourcesOnlyJar2", bmaker, jar);
			assertTrue(bmaker.getExports()
				.getByFQN("ro") != null);
			assertFalse(bmaker.getExports()
				.getByFQN("META-INF") != null);

			assertEquals(3, jar.getResources()
				.size());
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testClasspathFileNotExist() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			File cp[] = new File[] {
				IO.getFile("jar/idonotexist.jar")
			};

			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			bmaker.build();
			assertTrue(bmaker.check("The JAR is empty", "Missing file on classpath: .*/jar/idonotexist.jar"));
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testExpandWithNegate() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			File cp[] = new File[] {
				IO.getFile("jar/asm.jar")
			};

			p.put("Export-Package", "!org.objectweb.asm,*");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			assertNull(jar.getDirectories()
				.get("org/objectweb/asm"));
			assertNotNull(jar.getDirectories()
				.get("org/objectweb/asm/signature"));
			assertEquals(0, bmaker.getWarnings()
				.size());
			assertEquals(0, bmaker.getErrors()
				.size());
			assertEquals(3, jar.getResources()
				.size());
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testIncludeResourceResourcesOnlyJar() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			File cp[] = new File[] {
				IO.getFile("jar/ro.jar")
			};

			p.put("Export-Package", "*");
			p.put("Import-Package", "");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertEquals(0, bmaker.getWarnings()
				.size());
			assertEquals(0, bmaker.getErrors()
				.size());
			assertEquals(3, jar.getResources()
				.size());
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testIncludeResourceResourcesOnly() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			File cp[] = new File[] {
				new File("test")
			};

			p.put("Import-Package", "");
			p.put("Private-Package", "test.resourcesonly");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			assertEquals(0, bmaker.getWarnings()
				.size());
			assertEquals(0, bmaker.getErrors()
				.size());
			assertEquals(4, jar.getResources()
				.size());
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testIncludeResourceFromZipDefault() throws Exception {
		Builder bmaker = new Builder();
		Properties p = new Properties();
		p.put("Include-Resource", "@jar/easymock.jar");
		bmaker.setProperties(p);
		Jar jar = bmaker.build();
		assertTrue(bmaker.check());
		assertEquals(59, jar.getResources()
			.size());

	}

	@Test
	public void testIncludeResourceFromZipDeep() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put("Include-Resource", "@jar/easymock.jar!/**");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			assertEquals(59, jar.getResources()
				.size());
		} finally {
			bmaker.close();
		}
	}

	@Test
	public void testIncludeResourceFromZipOneDirectory() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put("Import-Package", "");
			p.put("Include-Resource", "@jar/easymock.jar!/org/easymock/**");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());

			assertEquals(59, jar.getResources()
				.size());
			assertNotNull(jar.getResource("org/easymock/AbstractMatcher.class"));
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testIncludeResourceFromZipOneDirectoryOther() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put(Constants.BUNDLE_CLASSPATH, "OPT-INF/test");
			p.put("Import-Package", "!*");
			p.put("-resourceonly", "true");
			p.put("Include-Resource", "OPT-INF/test=@jar/osgi.jar!/org/osgi/service/event/**");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();

			assertTrue(bmaker.check());

			assertEquals(7, jar.getResources()
				.size());
			assertNotNull(jar.getResource("OPT-INF/test/org/osgi/service/event/EventAdmin.class"));
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testIncludeResourceFromZipRecurseDirectory() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put("Import-Package", "!*");
			p.put("Include-Resource", "@jar/easymock.jar!/org/easymock/**");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			assertEquals(59, jar.getResources()
				.size());
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testIncludeResourceFromZipRecurseDirectoryFlatten() throws Exception {
		try (Builder bmaker = new Builder()) {
			Properties p = new Properties();
			p.put("Import-Package", "!*");
			p.put("-includeresource", "new.package/=@jar/cxf-rt-rs-sse-3.2.5.jar!/META-INF/services/*;flatten:=true");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			assertThat(jar.getResources().keySet())
					.containsExactlyInAnyOrder("new.package/javax.ws.rs.sse.SseEventSource$Builder",
						"new.package/org.apache.cxf.jaxrs.ext.JAXRSServerFactoryCustomizationExtension");
		}

	}

	@Test
	public void testIncludeResourceFromZipRecurseDirectoryRename2() throws Exception {
		try (Builder bmaker = new Builder()) {
			Properties p = new Properties();
			p.put("Import-Package", "!*");
			p.put("-includeresource", "new.package/=@jar/cxf-rt-rs-sse-3.2.5.jar!/META-INF/services/(*);rename:=$1");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			System.out.println(jar.getResources());
			assertThat(jar.getResources()
				.keySet()).containsExactlyInAnyOrder("new.package/javax.ws.rs.sse.SseEventSource$Builder",
					"new.package/org.apache.cxf.jaxrs.ext.JAXRSServerFactoryCustomizationExtension");
		}

	}

	@Test
	public void testIncludeResourceFromZipRecurseDirectoryRename3() throws Exception {
		try (Builder bmaker = new Builder()) {
			Properties p = new Properties();
			p.put("Import-Package", "!*");
			p.put("-includeresource",
				"new.package/=@jar/cxf-rt-rs-sse-3.2.5.jar!/(META-INF)/(cxf|services)/(*);rename:=$2/$1/$3.copy");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			System.out.println(jar.getResources());
			assertThat(jar.getResources()
				.keySet()).containsExactlyInAnyOrder(
					"new.package/services/META-INF/javax.ws.rs.sse.SseEventSource$Builder.copy",
					"new.package/services/META-INF/org.apache.cxf.jaxrs.ext.JAXRSServerFactoryCustomizationExtension.copy",
					"new.package/cxf/META-INF/bus-extensions.txt.copy");
		}

	}

	@Test
	public void testIncludeLicenseFromZip() throws Exception {
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put("Import-Package", "");
			p.put("Include-Resource", "@jar/osgi.jar!/LICENSE");
			bmaker.setProperties(p);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			assertEquals(1, jar.getResources()
				.size());
			assertNotNull(jar.getResource("LICENSE"));
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testEasymock() throws Exception {
		File cp[] = {
			IO.getFile("jar/easymock.jar")
		};
		Builder bmaker = new Builder();
		try {
			Properties p = new Properties();
			p.put("Import-Package", "*");
			p.put("Export-Package", "*");
			p.put("Bundle-SymbolicName", "easymock");
			p.put("Bundle-Version", "2.2");
			bmaker.setProperties(p);
			bmaker.setClasspath(cp);
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			jar.getManifest()
				.write(System.err);
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testSources() throws Exception {
		Builder bmaker = new Builder();
		try {
			bmaker.addClasspath(new File("bin_test"));
			bmaker.setSourcepath(new File[] {
				new File("test")
			});
			bmaker.setProperty("-sources", "true");
			bmaker.setProperty("Export-Package", "test.activator");
			Jar jar = bmaker.build();
			assertTrue(bmaker.check());
			assertEquals(
				"[test/activator/AbstractActivator.class, test/activator/Activator.class, test/activator/Activator11.class, test/activator/Activator2.class, test/activator/Activator3.class, test/activator/ActivatorPackage.class, test/activator/ActivatorPrivate.class, test/activator/DefaultVisibilityActivator.class, test/activator/IActivator.class, test/activator/MissingNoArgsConstructorActivator.class, test/activator/NotAnActivator.class]",
				new SortedList<>(jar.getDirectories()
					.get("test/activator")
					.keySet()).toString());
		} finally {
			bmaker.close();
		}

	}

	@Test
	public void testVerify() throws Exception {
		System.err.println("Erroneous bundle: tb1.jar");
		try (Jar jar = new Jar("test", getClass().getResourceAsStream("tb1.jar"));
			Verifier verifier = new Verifier(jar)) {
			verifier.verify();
			assertTrue(verifier.check());
		}
	}

	public static void report(String title, Analyzer builder, Jar jar) {
		System.err.println("Directories " + jar.getDirectories()
			.keySet());
		System.err.println("Warnings    " + builder.getWarnings());
		System.err.println("Errors      " + builder.getErrors());
		System.err.println("Exports     " + builder.getExports());
		System.err.println("Imports     " + builder.getImports());
	}

	@Test
	public void testImportBSN() throws Exception {
		try (Builder b = new Builder()) {
			b.addClasspath(new File("bin_test"));
			b.addClasspath(new File("jar/ecj-4.7.3a.jar"));
			b.addClasspath(new File("jar/org.eclipse.osgi-3.5.0.jar"));
			b.setProperty("Export-Package", "test.activator");
			b.setProperty("Import-Package",
				"org.eclipse.jdt.core.compiler;org.eclipse.osgi.framework.util;"
					+ "bundle-symbolic-name=\"${@bundlesymbolicname}\";"
					+ "bundle-version=\"${range;[==,+0);${@bundleversion}}\"");
			Jar jar = b.build();
			assertTrue(b.check());
			Manifest manifest = jar.getManifest();
			manifest.write(System.err);
			Domain d = Domain.domain(manifest);
			Parameters imports = d.getImportPackage();
			Attrs attrs = imports.get("org.eclipse.jdt.core.compiler");
			assertEquals("org.eclipse.jdt.core.compiler.batch", attrs.get("bundle-symbolic-name"));
			assertEquals("[3.13,4.0)", attrs.get("bundle-version"));
			attrs = imports.get("org.eclipse.osgi.framework.util");
			assertEquals("org.eclipse.osgi", attrs.get("bundle-symbolic-name"));
			assertEquals("[3.5,4.0)", attrs.get("bundle-version"));
		}
	}

	/**
	 * Tests that an Import-Package without a version-range creates a warning in
	 * pedantic mode.
	 */
	@Test
	public void testWarningImportsThatLackVersionRanges() throws Exception {
		try (Builder b = new Builder()) {
			b.setPedantic(true);
			b.setProperty("Import-Package", "foo.bar");
			b.build();
			softly.assertThat(b.check("Imports that lack version ranges: \\[foo.bar\\]",
				"The JAR is empty: The instructions for the JAR named biz.aQute.bndlib.tests did not cause any content to be included, this is likely wrong"))
				.isTrue();
			softly.assertThat(b.getImports()
				.keySet()).containsExactlyInAnyOrder(b.getPackageRef("foo.bar"));

		}
	}

	/**
	 * Tests that an Import-Package without a version-range for a JDK package
	 * creates NO warning in pedantic mode. This test requires Java 1.6 which
	 * contains a package, so we expect NO warning
	 */
	@Test
	public void testNoWarningImportsThatLackVersionRangesJDKPackages() throws Exception {
		try (Builder b = new Builder()) {
			b.setPedantic(true);

			RequirementBuilder rqb = new RequirementBuilder(EXECUTION_ENVIRONMENT_NAMESPACE);

			FilterBuilder fb = new FilterBuilder();
			EE ee = EE.JavaSE_1_6;
			fb.and()
				.eq(EXECUTION_ENVIRONMENT_NAMESPACE, ee.getCapabilityName())
				.in(CAPABILITY_VERSION_ATTRIBUTE, new VersionRange(ee.getCapabilityVersion()))
				.endAnd();
			rqb.addFilter(fb);

			// Require-Capability:
			// osgi.ee;filter:='(&(osgi.ee=JavaSE)(version>=1.6.0))'
			b.setProperty(Constants.REQUIRE_CAPABILITY, rqb.buildSyntheticRequirement()
				.toString());
			b.setProperty("Import-Package", "javax.xml.transform.stax");
			b.build();
			// there should be NO warning: Imports that lack version ranges:
			// [javax.xml.transform.stax]
			softly.assertThat(b.check(
				"The JAR is empty: The instructions for the JAR named biz.aQute.bndlib.tests did not cause any content to be included, this is likely wrong"))
				.isTrue();
			softly.assertThat(b.getImports()
				.keySet())
				.containsExactlyInAnyOrder(b.getPackageRef("javax.xml.transform.stax"));

		}
	}

	/**
	 * Tests that an Import-Package without a version-range for a JDK package
	 * creates 1 warning in pedantic mode. This test requires Java 1.5 which
	 * does NOT contains a package, so we expect 1 warning
	 */
	@Test
	public void testWarningImportsThatLackVersionRangesNotInJDK() throws Exception {
		try (Builder b = new Builder()) {
			b.setPedantic(true);

			RequirementBuilder rqb = new RequirementBuilder(EXECUTION_ENVIRONMENT_NAMESPACE);

			FilterBuilder fb = new FilterBuilder();
			EE ee = EE.J2SE_1_5;
			fb.and()
				.eq(EXECUTION_ENVIRONMENT_NAMESPACE, ee.getCapabilityName())
				.in(CAPABILITY_VERSION_ATTRIBUTE, new VersionRange(ee.getCapabilityVersion()))
				.endAnd();
			rqb.addFilter(fb);

			// Require-Capability:
			// osgi.ee;filter:='(&(osgi.ee=JavaSE)(version>=1.5.0))'
			// NOTE: Java 1.5. does not contain 'javax.xml.transform.stax' so we
			// expect a warning
			b.setProperty(Constants.REQUIRE_CAPABILITY, rqb.buildSyntheticRequirement()
				.toString());
			b.setProperty("Import-Package", "javax.xml.transform.stax");
			b.build();
			// there should be NO warning: Imports that lack version ranges:
			// [javax.xml.transform.stax]
			softly.assertThat(b.check("Imports that lack version ranges: \\[javax.xml.transform.stax\\]",
				"The JAR is empty: The instructions for the JAR named biz.aQute.bndlib.tests did not cause any content to be included, this is likely wrong"))
				.isTrue();
			softly.assertThat(b.getImports()
				.keySet())
				.containsExactlyInAnyOrder(b.getPackageRef("javax.xml.transform.stax"));

		}
	}
}
