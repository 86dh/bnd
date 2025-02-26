package aQute.bnd.maven.generate.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class GenerateMojo extends AbstractGenerateMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		doExecute(false);
	}

}
