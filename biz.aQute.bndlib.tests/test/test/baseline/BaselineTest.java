package test.baseline;

import static aQute.bnd.osgi.Constants.BUNDLE_SYMBOLICNAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import aQute.bnd.build.Project;
import aQute.bnd.build.ProjectBuilder;
import aQute.bnd.build.Workspace;
import aQute.bnd.differ.Baseline;
import aQute.bnd.differ.Baseline.BundleInfo;
import aQute.bnd.differ.Baseline.Info;
import aQute.bnd.differ.DiffPluginImpl;
import aQute.bnd.osgi.Analyzer;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Verifier;
import aQute.bnd.service.RepositoryPlugin;
import aQute.bnd.service.diff.Delta;
import aQute.bnd.service.diff.Diff;
import aQute.bnd.service.diff.Tree;
import aQute.bnd.test.jupiter.InjectTemporaryDirectory;
import aQute.bnd.version.Version;
import aQute.lib.collections.SortedList;
import aQute.lib.io.IO;
import aQute.libg.reporter.ReporterAdapter;

@SuppressWarnings("resource")
public class BaselineTest {

	Workspace	workspace;

	private Workspace getWorkspace(File tmp) throws Exception {
		if (workspace != null)
			return workspace;

		IO.copy(IO.getFile("testresources/ws"), tmp);
		return workspace = new Workspace(tmp);
	}

	@AfterEach
	protected void tearDown() throws Exception {
		IO.close(workspace);
		workspace = null;
	}

	public static class PrivateConstructorsAndFinal {
		public class Normal {}

		public class Private {
			private Private() {}
		}

		public class PrivateMultiple {
			private PrivateMultiple() {}

			private PrivateMultiple(int a) {}

			private PrivateMultiple(int a, int b) {}

			private PrivateMultiple(int a, int b, int c) {}
		}

		public class ProtectedPrivate {
			protected ProtectedPrivate(int x) {}
		}

		public final class PrivateFinal {
			private PrivateFinal() {}
		}

		public final class Final {}

	}

	@Test
	public void testTreatingPrivateConstructorsAsFinalClass() throws Exception {
		DiffPluginImpl diff = new DiffPluginImpl();
		diff.setIgnore("METHOD");
		try (Builder b = new Builder()) {
			b.addClasspath(IO.getFile("bin_test"));
			b.setProperty("Export-Package", "test.baseline");
			Jar build = b.build();
			assertThat(b.check()).isTrue();
			Tree tree = diff.tree(b);
			Tree pack = tree.get("<api>")
				.get("test.baseline");
			Tree Normal = pack.get("test.baseline.BaselineTest$PrivateConstructorsAndFinal$Normal");
			Tree Final = pack.get("test.baseline.BaselineTest$PrivateConstructorsAndFinal$Final");
			Tree PrivateFinal = pack.get("test.baseline.BaselineTest$PrivateConstructorsAndFinal$PrivateFinal");
			Tree Private = pack.get("test.baseline.BaselineTest$PrivateConstructorsAndFinal$Private");
			Tree ProtectedPrivate = pack.get("test.baseline.BaselineTest$PrivateConstructorsAndFinal$ProtectedPrivate");
			Tree PrivateMultiple = pack.get("test.baseline.BaselineTest$PrivateConstructorsAndFinal$PrivateMultiple");

			assertThat(Normal).isNotNull();
			assertThat(Final).isNotNull();
			assertThat(PrivateFinal).isNotNull();
			assertThat(Private).isNotNull();
			assertThat(ProtectedPrivate).isNotNull();
			assertThat(PrivateMultiple).isNotNull();

			assertThat(Normal.get("final")).isNull();
			assertThat(PrivateFinal.get("final")).isNotNull();
			assertThat(Private.get("final")).isNotNull();
			assertThat(Final.get("final")).isNotNull();
			assertThat(ProtectedPrivate.get("final")).isNull();
			assertThat(PrivateMultiple.get("final")).isNotNull();

			assertThat(Normal.diff(Private)
				.getDelta()).isEqualTo(Delta.MINOR);
			assertThat(Private.diff(Normal)
				.getDelta()).isEqualTo(Delta.MAJOR);

			assertThat(Private.diff(Final)
				.get("final")
				.getDelta()).isEqualTo(Delta.UNCHANGED);

			assertThat(Final.diff(Private)
				.get("final")
				.getDelta()).isEqualTo(Delta.UNCHANGED);

			assertThat(PrivateFinal.diff(Private)
				.get("final")
				.getDelta()).isEqualTo(Delta.UNCHANGED);
			assertThat(Private.diff(Final)
				.get("final")
				.getDelta()).isEqualTo(Delta.UNCHANGED);

			assertThat(ProtectedPrivate.diff(Final)
				.get("final")
				.getDelta()).isEqualTo(Delta.REMOVED);
			assertThat(Final.diff(ProtectedPrivate)
				.get("final")
				.getDelta()).isEqualTo(Delta.ADDED);
		}
	}

	/**
	 * Test 2 jars compiled with different compilers
	 */
	@Test
	public void testCompilerEnumDifference() throws Exception {
		DiffPluginImpl diff = new DiffPluginImpl();
		try (Jar ecj = new Jar(IO.getFile("jar/baseline/com.example.baseline.ecj.jar"));
			Jar javac = new Jar(IO.getFile("jar/baseline/com.example.baseline.javac.jar"));) {

			Tree tecj = diff.tree(ecj);
			Tree tjavac = diff.tree(javac);
			Diff d = tecj.diff(tjavac);
			assertEquals(Delta.UNCHANGED, d.getDelta());
		}
	}

	/**
	 * Test skipping classes when there is source
	 */
	@Test
	public void testClassesDiffWithSource() throws Exception {
		DiffPluginImpl diff = new DiffPluginImpl();
		try (Jar jar = new Jar(IO.getFile("jar/osgi.jar")); Jar out = new Jar(".");) {
			out.putResource("OSGI-OPT/src/org/osgi/application/ApplicationContext.java",
				jar.getResource("OSGI-OPT/src/org/osgi/application/ApplicationContext.java"));
			out.putResource("org/osgi/application/ApplicationContext.class",
				jar.getResource("org/osgi/application/ApplicationContext.class"));
			try (Analyzer a = new Analyzer(out)) {
				a.addClasspath(out);
				Tree tree = diff.tree(a);

				Tree src = tree.get("<resources>")
					.get("OSGI-OPT/src/org/osgi/application/ApplicationContext.java")
					.getChildren()[0];

				assertNotNull(src);

				assertNull(tree.get("<resources>")
					.get("org/osgi/application/ApplicationContext.class"));
			}
		}
	}

	@Test
	public void testClassesDiffWithoutSource() throws Exception {
		DiffPluginImpl diff = new DiffPluginImpl();
		try (Jar jar = new Jar(IO.getFile("jar/osgi.jar")); Jar out = new Jar(".");) {
			for (String path : jar.getResources()
				.keySet()) {
				if (!path.startsWith("OSGI-OPT/src/"))
					out.putResource(path, jar.getResource(path));
			}
			try (Analyzer a = new Analyzer(out)) {
				a.addClasspath(out);
				Tree tree = diff.tree(a);
				assertNull(tree.get("<resources>")
					.get("OSGI-OPT/src/org/osgi/application/ApplicationContext.java"));
				assertNotNull(tree.get("<resources>")
					.get("org/osgi/application/ApplicationContext.class"));
			}
		}
	}

	@Test
	public void testJava8DefaultMethods() throws Exception {
		try (Builder older = new Builder(); Builder newer = new Builder();) {
			older.addClasspath(IO.getFile("java8/older/bin"));
			older.setExportPackage("*;version=1.0");
			newer.addClasspath(IO.getFile("java8/newer/bin"));
			newer.setExportPackage("*;version=1.0");
			try (Jar o = older.build(); Jar n = newer.build();) {
				assertTrue(older.check());
				assertTrue(newer.check());

				DiffPluginImpl differ = new DiffPluginImpl();
				Baseline baseline = new Baseline(older, differ);

				Set<Info> infoSet = baseline.baseline(n, o, null);
				assertEquals(1, infoSet.size());
				for (Info info : infoSet) {
					assertTrue(info.mismatch);
					assertEquals(new Version(1, 1, 0), info.suggestedVersion);
					assertEquals(info.packageName, "api_default_methods");
				}
			}
		}
	}

	@Test
	public void testNoMismatchForZeroMajor() throws Exception {
		try (Builder older = new Builder(); Builder newer = new Builder();) {
			older.addClasspath(IO.getFile("java8/older/bin"));
			older.setExportPackage("*;version=0.1");
			newer.addClasspath(IO.getFile("java8/newer/bin"));
			newer.setExportPackage("*;version=0.1");
			try (Jar o = older.build(); Jar n = newer.build();) {
				assertTrue(older.check());
				assertTrue(newer.check());

				DiffPluginImpl differ = new DiffPluginImpl();
				Baseline baseline = new Baseline(older, differ);

				Set<Info> infoSet = baseline.baseline(n, o, null);
				assertEquals(1, infoSet.size());
				for (Info info : infoSet) {
					assertFalse(info.mismatch);
					assertEquals(new Version(0, 2, 0), info.suggestedVersion);
					assertEquals(info.packageName, "api_default_methods");
				}
			}
		}
	}

	/**
	 * Check if we can ignore resources in the baseline. First build two jars
	 * that are identical except for the b/b resource. Then do baseline on them.
	 */
	@Test
	public void testIgnoreResourceDiff() throws Exception {
		Processor processor = new Processor();
		DiffPluginImpl differ = new DiffPluginImpl();
		differ.setIgnore("b/b");
		Baseline baseline = new Baseline(processor, differ);

		try (Builder a = new Builder(); Builder b = new Builder();) {
			a.setProperty("-includeresource", "a/a;literal='aa',b/b;literal='bb'");
			a.setProperty("-resourceonly", "true");
			b.setProperty("-includeresource", "a/a;literal='aa',b/b;literal='bbb'");
			b.setProperty("-resourceonly", "true");
			try (Jar aj = a.build(); Jar bj = b.build();) {
				Set<Info> infoSet = baseline.baseline(aj, bj, null);

				BundleInfo binfo = baseline.getBundleInfo();
				assertFalse(binfo.mismatch);
			}
		}
	}

	/**
	 * When a JAR is build the manifest is not set in the resources but in a
	 * instance var.
	 *
	 * @throws Exception
	 */
	@Test
	public void testPrematureJar() throws Exception {
		File file = IO.getFile(new File(""), "jar/osgi.jar");
		try (Builder b1 = new Builder(); Builder b2 = new Builder();) {
			b1.addClasspath(file);
			b1.setProperty(Constants.BUNDLE_VERSION, "1.0.0.${tstamp}");
			b1.setExportPackage("org.osgi.service.event");
			try (Jar j1 = b1.build();) {
				assertTrue(b1.check());

				File tmp = new File("tmp.jar");
				j1.write(tmp);
				try (Jar j11 = new Jar(tmp);) {
					Thread.sleep(2000);

					b2.addClasspath(file);
					b2.setProperty(Constants.BUNDLE_VERSION, "1.0.0.${tstamp}");
					b2.setExportPackage("org.osgi.service.event");

					try (Jar j2 = b2.build();) {
						assertTrue(b2.check());

						DiffPluginImpl differ = new DiffPluginImpl();

						ReporterAdapter ra = new ReporterAdapter();
						Baseline baseline = new Baseline(ra, differ);
						ra.setTrace(true);
						ra.setPedantic(true);
						Set<Info> infos = baseline.baseline(j2, j11, null);
						print(baseline.getDiff(), " ");

						assertEquals(Delta.UNCHANGED, baseline.getDiff()
							.getDelta());
					}
				} finally {
					tmp.delete();
				}
			}
		}
	}

	static Pattern VERSION_HEADER_P = Pattern.compile("Bundle-Header:(" + Verifier.VERSION_STRING + ")",
		Pattern.CASE_INSENSITIVE);

	void print(Diff diff, String indent) {
		if (diff.getDelta() == Delta.UNCHANGED)
			return;

		System.out.println(indent + " " + diff);
		for (Diff sub : diff.getChildren()) {
			print(sub, indent + " ");
		}
	}

	/**
	 * In repo:
	 *
	 * <pre>
	 *  p3-1.1.0.jar p3-1.2.0.jar
	 * </pre>
	 *
	 * @throws Exception
	 */
	@Test
	public void testRepository(@InjectTemporaryDirectory
	File tmp) throws Exception {
		Jar v1_2_0_a = mock(Jar.class);
		when(v1_2_0_a.getVersion()).thenReturn("1.2.0.b");
		when(v1_2_0_a.getBsn()).thenReturn("p3");

		RepositoryPlugin repo = mock(RepositoryPlugin.class);
		when(repo.get(anyString(), any(Version.class), anyMap()))
			.thenReturn(IO.getFile("testresources/ws/cnf/releaserepo/p3/p3-1.2.0.jar"));
		System.out.printf("get %s%n", repo.get("p3", new Version("1.2.0.b"), new HashMap<>()));

		when(repo.canWrite()).thenReturn(true);
		when(repo.getName()).thenReturn("Baseline");
		when(repo.versions("p3")).thenReturn(new SortedList<>(new Version("1.1.0.a"), new Version("1.1.0.b"),
			new Version("1.2.0.a"), new Version("1.2.0.b")));
		getWorkspace(tmp).addBasicPlugin(repo);

		Project p3 = getWorkspace(tmp).getProject("p3");
		p3.setBundleVersion("1.3.0");
		ProjectBuilder builder = (ProjectBuilder) p3.getBuilder(null)
			.getSubBuilder();
		builder.setProperty(Constants.BASELINE, "*");
		builder.setProperty(Constants.BASELINEREPO, "Baseline");

		// Nothing specified
		Jar jar = builder.getBaselineJar();
		assertEquals("1.2.0", new Version(jar.getVersion()).getWithoutQualifier()
			.toString());

		if (!builder.check())
			fail(builder.getErrors()
				.toString());
		{
			// check for error when repository contains later versions
			builder = (ProjectBuilder) p3.getBuilder(null)
				.getSubBuilder();
			builder.setBundleVersion("1.1.3");
			builder.setTrace(true);
			builder.setProperty(Constants.BASELINE, "*");
			builder.setProperty(Constants.BASELINEREPO, "Baseline");
			jar = builder.getBaselineJar();
			assertNull(jar);

			if (!builder.check("The baseline version 1.2.0.b is higher than the current version 1.1.3 for p3"))
				fail(builder.getErrors()
					.toString());
		}
		{
			// check for no error when repository has the same version
			builder = (ProjectBuilder) p3.getBuilder(null)
				.getSubBuilder();
			builder.setBundleVersion("1.2.0.b");
			builder.setTrace(true);
			builder.setProperty(Constants.BASELINE, "*");
			builder.setProperty(Constants.BASELINEREPO, "Baseline");
			jar = builder.getBaselineJar();
			assertNotNull(jar);

			if (!builder.check())
				fail(builder.getErrors()
					.toString());

		}
		{
			// check for no error when repository has the same version
			builder = (ProjectBuilder) p3.getBuilder(null)
				.getSubBuilder();
			builder.setBundleVersion("1.2.0.b");
			builder.setTrace(true);
			builder.setProperty(Constants.BASELINE, "*");
			builder.setProperty(Constants.BASELINEREPO, "Baseline");
			builder.build();

			if (!builder.check("The bundle version \\(1.2.0/1.2.0\\) is too low, must be at least 1.3.0"))
				fail(builder.getErrors()
					.toString());

		}
	}

	/**
	 * Check what happens when there is nothing in the repo ... We do not
	 * generate an error when version <=1.0.0, otherwise we generate an error.
	 *
	 * @throws Exception
	 */
	@Test
	public void testNothingInRepo(@InjectTemporaryDirectory
	File tmp) throws Exception {
		try {
			RepositoryPlugin repo = mock(RepositoryPlugin.class);
			when(repo.canWrite()).thenReturn(true);
			when(repo.getName()).thenReturn("Baseline");
			when(repo.versions("p3")).thenReturn(new TreeSet<>());
			getWorkspace(tmp).addBasicPlugin(repo);
			Project p3 = getWorkspace(tmp).getProject("p3");
			p3.setProperty(Constants.BASELINE, "*");
			p3.setProperty(Constants.BASELINEREPO, "Baseline");
			p3.setBundleVersion("0");
			p3.build();
			assertTrue(p3.check());

			p3.setBundleVersion("1.0.0.XXXXXX");
			p3.build();
			assertTrue(p3.check());

			p3.setBundleVersion("1.0.1");
			p3.build();
			assertTrue(p3.check("There is no baseline for p3 in the baseline repo"));

			p3.setBundleVersion("1.1");
			p3.build();
			assertTrue(p3.check("There is no baseline for p3 in the baseline repo"));

			p3.setBundleVersion("2.0.0.XXXXXX");
			p3.build();
			assertTrue(p3.check());

			p3.setBundleVersion("2.0.1");
			p3.build();
			assertTrue(p3.check("There is no baseline for p3 in the baseline repo"));

			p3.setBundleVersion("2.1");
			p3.build();
			assertTrue(p3.check("There is no baseline for p3 in the baseline repo"));
		} finally {
			IO.delete(tmp);
		}
	}

	// Adding a method to a ProviderType produces a MINOR bump (1.0.0 -> 1.1.0)
	@Test
	public void testProviderTypeBump() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("testresources/api-orig.jar"));
			Jar newer = new Jar(IO.getFile("testresources/api-providerbump.jar"));) {

			Set<Info> infoSet = baseline.baseline(newer, older, null);
			System.out.println(differ.tree(newer)
				.get("<api>"));

			assertEquals(1, infoSet.size());
			Info info = infoSet.iterator()
				.next();

			assertTrue(info.mismatch);
			assertEquals("dummy.api", info.packageName);
			assertEquals("1.1.0", info.suggestedVersion.toString());
		}
	}

	// Adding a method to a ConsumerType produces a MINOR bump (1.0.0 -> 2.0.0)
	@Test
	public void testConsumerTypeBump() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("testresources/api-orig.jar"));
			Jar newer = new Jar(IO.getFile("testresources/api-consumerbump.jar"));) {

			Set<Info> infoSet = baseline.baseline(newer, older, null);

			assertEquals(1, infoSet.size());
			Info info = infoSet.iterator()
				.next();

			assertTrue(info.mismatch);
			assertEquals("dummy.api", info.packageName);
			assertEquals("2.0.0", info.suggestedVersion.toString());
		}
	}

	// Adding a method to a ProviderType produces a MINOR bump (1.0.0 -> 1.1.0)
	@Test
	public void testBundleVersionBump() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("testresources/api-orig.jar"));
			Jar newer = new Jar(IO.getFile("testresources/api-providerbump.jar"));) {

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertTrue(bundleInfo.mismatch);
			assertEquals("1.1.0", bundleInfo.suggestedVersion.toString());
		}
	}

	// Adding a method to a ProviderType produces a MINOR bump (1.0.0 -> 1.1.0)
	// in package, but bundle version should be ignored
	@Test
	public void testBundleVersionDiffignore() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		differ.setIgnore("Bundle-Version");
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("testresources/api-orig.jar"));
			Jar newer = new Jar(IO.getFile("testresources/api-providerbump.jar"));) {

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertFalse(bundleInfo.mismatch);
		}
	}

	// Adding a method to a ProviderType produces a MINOR bump (1.0.0 -> 1.1.0)
	@Test
	public void testBundleVersionBumpDifferentSymbolicNames() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("testresources/api-orig.jar"));
			Jar newer = new Jar(IO.getFile("testresources/api-providerbump.jar"));) {

			newer.getManifest()
				.getMainAttributes()
				.putValue(BUNDLE_SYMBOLICNAME, "a.different.name");

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertFalse(bundleInfo.mismatch);
			assertEquals(newer.getVersion(), bundleInfo.suggestedVersion.toString());
		}
	}

	// Adding a method to an exported class produces a MINOR bump (1.0.0 ->
	// 1.1.0)
	@Test
	public void testMinorChange() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("testresources/minor-and-removed-change-1.0.0.jar"));
			Jar newer = new Jar(IO.getFile("testresources/minor-change-1.0.1.jar"));) {

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertTrue(bundleInfo.mismatch);
			assertEquals("1.1.0", bundleInfo.suggestedVersion.toString());
		}
	}

	// Adding a method to an exported class and unexporting a package produces a
	// MINOR bump (1.0.0 -> 1.1.0)
	@Test
	public void testMinorAndRemovedChange() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("testresources/minor-and-removed-change-1.0.0.jar"));
			Jar newer = new Jar(IO.getFile("testresources/minor-and-removed-change-1.0.1.jar"));) {

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertTrue(bundleInfo.mismatch);
			assertEquals("2.0.0", bundleInfo.suggestedVersion.toString());
		}
	}

	// Deleting a protected field on a ProviderType API class produces a MINOR
	// bump (1.0.0 -> 1.1.0)
	@Test
	public void testProviderProtectedFieldRemovedChange() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("jar/baseline/provider-deletion-1.0.0.jar"));
			Jar newer = new Jar(IO.getFile("jar/baseline/provider-deletion-1.1.0.jar"));) {

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertFalse(bundleInfo.mismatch);
			assertEquals("1.1.0", bundleInfo.suggestedVersion.toString());

			Set<Info> packageInfos = baseline.getPackageInfos();

			assertEquals(1, packageInfos.size());

			Info change = packageInfos.iterator()
				.next();
			assertTrue(change.mismatch);
			assertEquals("bnd.baseline.test", change.packageName);
			assertEquals("1.1.0", change.suggestedVersion.toString());
		}
	}

	// Moving a package from the root into a jar on the Bundle-ClassPath
	// should not result in DELETED
	@Test
	public void testMovePackageToBundleClassPath() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("jar/baseline/com.liferay.calendar.api-2.0.5.jar"));
			Jar newer = new Jar(IO.getFile("jar/baseline/com.liferay.calendar.api-2.1.0.jar"));) {

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertFalse(bundleInfo.mismatch);
			assertEquals("2.1.0", bundleInfo.suggestedVersion.toString());

			Set<Info> packageInfos = baseline.getPackageInfos();

			assertEquals(12, packageInfos.size());

			Info change = packageInfos.iterator()
				.next();
			assertFalse(change.mismatch);
			assertEquals("com.google.ical.iter", change.packageName);
			assertEquals("20110304.0.0", change.suggestedVersion.toString());
		}
	}

	// This tests the scenario where a super type is injected into the class
	// hierarchy but the super class comes from outside the bundle so that the
	// baseline cannot find it. Since the class hierarchy was cut off, the
	// baseline would _forget_ that every class inherits from Object, and _lose_
	// Object's methods if not directly implemented.
	@Test
	public void testCutOffInheritance() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("jar/baseline/inheritance-change-1.0.0.jar"));
			Jar newer = new Jar(IO.getFile("jar/baseline/inheritance-change-1.1.0.jar"));) {

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertFalse(bundleInfo.mismatch);
			assertEquals("1.1.0", bundleInfo.suggestedVersion.toString());

			Set<Info> packageInfos = baseline.getPackageInfos();

			assertEquals(1, packageInfos.size());

			Info change = packageInfos.iterator()
				.next();
			assertFalse(change.mismatch);
			assertEquals("example", change.packageName);
			assertEquals("1.1.0", change.suggestedVersion.toString());

			Diff packageDiff = change.packageDiff;

			Collection<Diff> children = packageDiff.getChildren();

			assertEquals(5, children.size());

			Iterator<Diff> iterator = children.iterator();

			Diff diff = iterator.next();
			assertEquals(Delta.MICRO, diff.getDelta());
			diff = iterator.next();
			assertEquals(Delta.MICRO, diff.getDelta());
			diff = iterator.next();
			assertEquals(Delta.MINOR, diff.getDelta());
		}
	}

	// This tests the scenario where the return type of an interface method is
	// expanded through generics.
	// e.g. from:
	// Foo getFoo();
	// to:
	// <T extends Foo> T getFoo();
	// or:
	// <T extends Foo & Comparable<Foo>> T getFoo();
	@Test
	public void testExpandErasureOfMethodReturn() throws Exception {
		Processor processor = new Processor();

		DiffPluginImpl differ = new DiffPluginImpl();
		Baseline baseline = new Baseline(processor, differ);

		try (Jar older = new Jar(IO.getFile("jar/baseline/expanding-erasure-1.0.0.jar"));
			Jar newer = new Jar(IO.getFile("jar/baseline/expanding-erasure-1.1.0.jar"));) {

			baseline.baseline(newer, older, null);

			BundleInfo bundleInfo = baseline.getBundleInfo();

			assertFalse(bundleInfo.mismatch);
			assertEquals("1.1.0", bundleInfo.suggestedVersion.toString());

			Set<Info> packageInfos = baseline.getPackageInfos();

			assertEquals(1, packageInfos.size());

			Info change = packageInfos.iterator()
				.next();
			assertFalse(change.mismatch);
			assertEquals("bnd.test", change.packageName);
			assertEquals("1.0.0", change.suggestedVersion.toString());

			Diff packageDiff = change.packageDiff;

			Collection<Diff> children = packageDiff.getChildren();

			assertEquals(4, children.size());

			Iterator<Diff> iterator = children.iterator();

			Diff diff = iterator.next();
			assertEquals(Delta.UNCHANGED, diff.getDelta());
		}
	}

}
