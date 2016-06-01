package aQute.maven.provider;

import java.io.File;
import java.util.List;

import aQute.bnd.http.HttpClient;
import aQute.http.testservers.HttpTestServer.Config;
import aQute.lib.io.IO;
import aQute.libg.reporter.ReporterAdapter;
import aQute.maven.api.IPom;
import aQute.maven.api.MavenScope;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import junit.framework.TestCase;

public class CentralTest extends TestCase {
	private static final String	REPO_URL	= "https://repo1.maven.org/maven2/";
	String						tmpName;
	File						local;

	List<MavenBackingRepository>	repo;
	MavenRepository				storage;
	ReporterAdapter				reporter	= new ReporterAdapter(System.err);

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		tmpName = "generated/tmp/test/" + getName();
		local = IO.getFile(tmpName + "/local");
		reporter.setTrace(true);
		Config config = new Config();
		IO.delete(local);
		local.mkdirs();
		repo = MavenRemoteRepository.create(REPO_URL, reporter, local, new HttpClient());
		storage = new MavenRepository(local, "central", this.repo, null, null,
				new ReporterAdapter(System.out), null);
	}

	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		storage.close();
	}

	public void testBasic() throws Exception {
		Revision r = Program.valueOf("org.lunarray.model.extensions.descriptor", "spring").version("1.0");
		IPom pom = storage.getPom(r);
		assertNotNull(pom);
		System.out.println(pom.getDependencies(MavenScope.compile, true));
	}


}
