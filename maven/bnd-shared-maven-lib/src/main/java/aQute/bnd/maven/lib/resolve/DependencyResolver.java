package aQute.bnd.maven.lib.resolve;

import aQute.bnd.repository.fileset.FileSetRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionResult;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DependencyResolver {

	private static final Logger logger = LoggerFactory.getLogger(DependencyResolver.class);

	private final boolean includeTransitive;
	private final MavenProject project;
	private final List<String> scopes;
	private final RepositorySystemSession session;
	private final RepositorySystem system;
	private final ProjectDependenciesResolver resolver;
	private final PostProcessor postProcessor;

	private Map<File,ArtifactResult> resolvedDependencies;

	/**
	 * Shortcut with {@code scopes = ['compile', 'runtime']},
	 * {@code includeTransitive = true}, and always local resources.
	 *
	 * @param project
	 * @param session
	 * @param resolver
	 * @param system
	 */
	public DependencyResolver(
		MavenProject project, RepositorySystemSession session, ProjectDependenciesResolver resolver,
		RepositorySystem system) {

		this(
			project, session, resolver, system, Arrays.asList("compile", "runtime"), true,
			new LocalPostProcessor());
	}

	public DependencyResolver(
		MavenProject project, RepositorySystemSession session, ProjectDependenciesResolver resolver,
		RepositorySystem system, List<String> scopes, boolean includeTransitive, PostProcessor postProcessor) {

		this.project = project;
		this.session = session;
		this.resolver = resolver;
		this.system = system;
		this.scopes = scopes;
		this.includeTransitive = includeTransitive;
		this.postProcessor = postProcessor;
	}

	public Map<File, ArtifactResult> resolve() throws MojoExecutionException {
		if (resolvedDependencies != null) {
			return resolvedDependencies;
		}

		DependencyResolutionRequest request = new DefaultDependencyResolutionRequest(project, session);

		request.setResolutionFilter(new DependencyFilter() {
			@Override
			public boolean accept(DependencyNode node, List<DependencyNode> parents) {
				if (node.getDependency() != null) {
					return scopes.contains(node.getDependency().getScope());
				}
				return false;
			}
		});

		DependencyResolutionResult result;
		try {
			result = resolver.resolve(request);
		} catch (DependencyResolutionException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}

		Map<File,ArtifactResult> dependencies = new HashMap<>();

		DependencyNode dependencyGraph = result.getDependencyGraph();

		if (dependencyGraph != null && !dependencyGraph.getChildren().isEmpty()) {
			List<RemoteRepository> remoteRepositories = new ArrayList<>(project.getRemoteProjectRepositories());

			ArtifactRepository deployRepo = project.getDistributionManagementArtifactRepository();

			if (deployRepo != null) {
				remoteRepositories.add(RepositoryUtils.toRepo(deployRepo));
			}

			discoverArtifacts(dependencies, dependencyGraph.getChildren(), project.getArtifact().getId(),
					remoteRepositories);
		}

		return resolvedDependencies = dependencies;
	}

	public FileSetRepository getFileSetRepository(
			String name, Collection<File> bundlesInputParameter, boolean useDefaults)
		throws Exception {

		Collection<File> bundles = new ArrayList<>();
		if (useDefaults) {
			Map<File,ArtifactResult> dependencies = resolve();
			bundles.addAll(dependencies.keySet());

			// TODO Find a better way to get all the artifacts produced by this project!!!
			File current = new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".jar");
			if (current.exists() && !bundles.contains(current)) {
				bundles.add(current);
			}
		}

		if (bundlesInputParameter != null) {
			bundles.addAll(bundlesInputParameter);
		}

		return new FileSetRepository(name, bundles);
	}

	private void discoverArtifacts(Map<File,ArtifactResult> files, List<DependencyNode> nodes, String parent,
			List<RemoteRepository> remoteRepositories)
			throws MojoExecutionException {

		for (DependencyNode node : nodes) {
			// Ensure that the file is downloaded so we can index it
			try {
				ArtifactResult resolvedArtifact = postProcessor.postProcessResult(system.resolveArtifact(
						session, new ArtifactRequest(node.getArtifact(), remoteRepositories, parent)));
				logger.debug("Located file: {} for artifact {}", resolvedArtifact.getArtifact().getFile(),
						resolvedArtifact);

				files.put(resolvedArtifact.getArtifact().getFile(), resolvedArtifact);
			} catch (ArtifactResolutionException e) {
				throw new MojoExecutionException("Failed to resolve the dependency " + node.getArtifact().toString(),
						e);
			}
			if (includeTransitive) {
				discoverArtifacts(files, node.getChildren(), node.getRequestContext(), remoteRepositories);
			} else {
				logger.debug("Ignoring transitive dependencies of {}", node.getDependency());
			}
		}
	}

}
