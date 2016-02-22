/**
 * BndBuilderPlugin for Gradle.
 *
 * <p>
 * The plugin name is {@code biz.aQute.bnd.builder}.
 *
 * <p>
 * This plugin applies the java plugin to a project and modifies the jar
 * task by adding the properties from the {@link BundleTaskConvention}
 * and building the jar file as a bundle.
 * <p>
 * This plugin also defines a 'baseline' configuration and a baseline task
 * of type {@link Baseline}. The baseline task will be set up with the
 * default of baselining the output of the jar task using the baseline
 * configuration. If the baseline configuration is not otherwise
 * setup and the baseline task is configured to baseline a task, the
 * baseline configuration will be set as follows:
 *
 * <pre>
 * dependencies {
 *     baseline('group': project.group, 
 *              'name': baseline.bundleTask.baseName, 
 *              'version': "(,${baseline.bundleTask.version})") {
 *       transitive false
 *     }
 *   }
 * }
 * </pre>
 */

package aQute.bnd.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

public class BndBuilderPlugin implements Plugin<Project> {
  public static final String PLUGINID = 'biz.aQute.bnd.builder'

  /**
   * Apply the {@code biz.aQute.bnd.builder} plugin to the specified project.
   */
  @Override
  public void apply(Project p) {
    p.configure(p) { project ->
      if (plugins.hasPlugin(BndPlugin.PLUGINID)) {
          throw new GradleException("Project already has '${BndPlugin.PLUGINID}' plugin applied.")
      }
      plugins.apply 'java'

      jar {
        description 'Assembles a bundle containing the main classes.'
        convention.plugins.bundle = new BundleTaskConvention(jar)
        doLast {
          buildBundle()
        }
      }

      configurations {
        baseline
      }

      task ('baseline', type: Baseline) {
        description 'Baseline the project bundle.'
        group 'release'
        bundle jar
        baseline configurations.baseline
      }

      afterEvaluate {
        if (baseline.bundleTask && (baseline.baselineConfiguration == configurations.baseline) && configurations.baseline.dependencies.empty) {
          def baselineDep = dependencies.create('group': group, 'name': baseline.bundleTask.baseName, 'version': "(,${baseline.bundleTask.version})")
          if (configurations.detachedConfiguration(baselineDep).setTransitive(false).resolvedConfiguration.hasError()) {
            dependencies {
              baseline files(baseline.bundle)
            }
          } else {
            dependencies {
              baseline(baselineDep) {
                transitive false
              }
            }
          }
        }
      }
    }
  }
}
