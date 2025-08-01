---
layout: default
title: Test-Cases CLASS ( ',' CLASS ) *
class: Header
summary: |
   Header to automatically execute tests in the bnd JUnit tester
note: AUTO-GENERATED FILE - DO NOT EDIT. You can add manual content via same filename in ext folder. 
---

- Example: `Test-Cases: com.foo.bar.MyTest`

- Pattern: `.*`

<!-- Manual content from: ext/test_cases.md --><br /><br />

# Test-Cases

The `Test-Cases` header specifies one or more test classes to be automatically executed by the bnd JUnit tester. This header is used to configure which tests should run when the bundle is tested in an OSGi environment.

Example:

```
Test-Cases: com.example.MyTest, com.example.AnotherTest
```

This header is typically set by build tooling or annotations and is required for automatic test execution with bnd's JUnit integration.


	private void checkForTesting(Project project, Properties properties) throws Exception {

		//
		// Only run junit when we have a test src directory
		//

		boolean junit = project.getTestSrc().isDirectory() && !Processor.isTrue(project.getProperty(Constants.NOJUNIT));
		boolean junitOsgi = project.getProperties().getProperty(Constants.TESTCASES) != null
				&& !Processor.isTrue(project.getProperty(Constants.NOJUNITOSGI));

		if (junit)
			properties.setProperty("project.junit", "true");
		if (junitOsgi)
			properties.setProperty("project.osgi.junit", "true");
	}

	public void test(List<String> tests) throws Exception {

		String testcases = getProperties().getProperty(Constants.TESTCASES);
		if (testcases == null) {
			warning("No %s set", Constants.TESTCASES);
			return;
		}
		clear();

		ProjectTester tester = getProjectTester();
		if ( tests != null) {
			trace("Adding tests %s", tests);
			for ( String test : tests) {
				tester.addTest(test);
			}
		}
		tester.setContinuous(isTrue(getProperty(Constants.TESTCONTINUOUS)));
		tester.prepare();

		if (!isOk()) {
			return;
		}
		int errors = tester.test();
		if (errors == 0) {
			System.err.println("No Errors");
		} else {
			if (errors > 0) {
				System.err.println(errors + " Error(s)");

			} else
				System.err.println("Error " + errors);
		}
	}

		void automatic() throws IOException {
		String testerDir = context.getProperty(TESTER_DIR);
		if (testerDir == null)
			testerDir = "testdir";

		final File reportDir = new File(testerDir);
		final List<Bundle> queue = new Vector<Bundle>();
		if (!reportDir.exists() && !reportDir.mkdirs()) {
			throw new IOException("Could not create directory " + reportDir);
		}
		trace("using %s, needed creation %s", reportDir, reportDir.mkdirs());

		trace("adding Bundle Listener for getting test bundle events");
		context.addBundleListener(new SynchronousBundleListener() {
			public void bundleChanged(BundleEvent event) {
				if (event.getType() == BundleEvent.STARTED) {
					checkBundle(queue, event.getBundle());
				}

			}
		});

		for (Bundle b : context.getBundles()) {
			checkBundle(queue, b);
		}

		trace("starting queue");
		int result = 0;
		outer: while (active) {
			Bundle bundle;
			synchronized (queue) {
				while (queue.isEmpty() && active) {
					try {
						queue.wait();
					}
					catch (InterruptedException e) {
						trace("tests bundle queue interrupted");
						thread.interrupt();
						break outer;
					}
				}
			}
			try {
				bundle = queue.remove(0);
				trace("received bundle to test: %s", bundle.getLocation());
				Writer report = getReportWriter(reportDir, bundle);
				try {
					trace("test will run");
					result += test(bundle, (String) bundle.getHeaders().get(aQute.bnd.osgi.Constants.TESTCASES), report);
					trace("test ran");
					if (queue.isEmpty() && !continuous) {
						trace("queue " + queue);
						System.exit(result);
					}
				}
				finally {
					if (report != null)
						report.close();
				}
			}
			catch (Exception e) {
				error("Not sure what happened anymore %s", e);
				System.exit(254);
			}
		}
	}
	
	
		void checkBundle(List<Bundle> queue, Bundle bundle) {
		if (bundle.getState() == Bundle.ACTIVE) {
			String testcases = (String) bundle.getHeaders().get(aQute.bnd.osgi.Constants.TESTCASES);
			if (testcases != null) {
				trace("found active bundle with test cases %s : %s", bundle, testcases);
				synchronized (queue) {
					queue.add(bundle);
					queue.notifyAll();
				}
			}
		}
	}

	
	
	
		if (testcases == null) {
//			if ( !continuous) {
//				System.err.println("\nThe -testcontinuous property must be set if invoked without arguments\n");
//				System.exit(255);
//			}
				
			trace("automatic testing of all bundles with " + aQute.bnd.osgi.Constants.TESTCASES + " header");
			try {
				automatic();
			}
			catch (IOException e) {
				// ignore
			}


<hr />
TODO Needs review - AI Generated content
