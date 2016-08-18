package aQute.bnd.repository.maven.provider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.util.function.Function;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import aQute.bnd.header.Attrs;
import aQute.bnd.osgi.Domain;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.bnd.service.repository.Phase;
import aQute.bnd.service.repository.SearchableRepository.ResourceDescriptor;
import aQute.bnd.version.Version;
import aQute.lib.collections.MultiMap;
import aQute.lib.io.IO;
import aQute.lib.json.JSONCodec;
import aQute.lib.strings.Strings;
import aQute.libg.cryptography.SHA1;
import aQute.libg.cryptography.SHA256;
import aQute.maven.api.Archive;
import aQute.maven.api.IMavenRepo;
import aQute.maven.api.Program;
import aQute.service.reporter.Reporter;

/**
 * Maintains an index of descriptors of archives. These descriptors are stored
 * in the maven repository
 */
class IndexFile {

	private static final JSONCodec CODEC = new JSONCodec();

	public class BundleDescriptor extends ResourceDescriptor {
		public long		lastModified;
		public Archive	archive;
		public boolean	merged;
		String			error;
		Promise<File>	promise;
		Resource		resource;

		public synchronized Resource getResource() {
			if (resource == null) {

				try {
					File f = promise.getValue();
					ResourceBuilder rb = new ResourceBuilder();
					rb.addFile(f, f.toURI());
					resource = rb.build();
				} catch (Exception e) {
					e.printStackTrace();
					reporter.exception(e, "Failed to get file for %s", archive);
					return resource = ResourceUtils.DUMMY_RESOURCE;
				}
			}
			return resource;
		}
	}

	final ConcurrentMap<Archive,Promise<File>>		promises	= new ConcurrentHashMap<>();
	final ConcurrentMap<Archive,BundleDescriptor>	descriptors	= new ConcurrentHashMap<>();
	final File										indexFile;
	final File										cacheDir;
	final IMavenRepo								repo;
	final Reporter									reporter;
	private long									lastModified;
	private AtomicBoolean							refresh		= new AtomicBoolean();

	IndexFile(Reporter reporter, File file, IMavenRepo repo) throws Exception {
		this.reporter = reporter;
		this.indexFile = file;
		this.repo = repo;
		this.cacheDir = new File(indexFile.getParentFile(), indexFile.getName() + ".info");
	}

	void open() throws Exception {
		loadIndexFile();
		sync();
	}

	void sync() {
		for (Iterator<Entry<Archive,Promise<File>>> i = promises.entrySet().iterator(); i.hasNext();) {
			Entry<Archive,Promise<File>> entry = i.next();
			try {
				Promise<File> f = entry.getValue();
				f.getValue();
			} catch (Exception e) {
				reporter.exception(e, "Failed to sync %s", entry.getKey());
			}
			i.remove();
		}
	}

	BundleDescriptor add(Archive archive) throws Exception {
		BundleDescriptor old = descriptors.putIfAbsent(archive, createInitialDescriptor(archive));
		BundleDescriptor descriptor = descriptors.get(archive);
		updateDescriptor(descriptor, repo.get(archive).getValue());
		if (old == null || !Arrays.equals(descriptor.id, old.id)) {
			saveIndexFile();
			return descriptor;
		}
		return null;
	}

	BundleDescriptor remove(Archive archive) throws Exception {
		BundleDescriptor descriptor = descriptors.remove(archive);
		if (descriptor != null) {
			saveIndexFile();
		}
		return descriptor;
	}

	public void remove(String bsn) throws Exception {
		for (Iterator<BundleDescriptor> bd = descriptors.values().iterator(); bd.hasNext();) {
			if (isBsn(bsn, bd.next()))
				bd.remove();
		}
		saveIndexFile();
	}

	Collection<String> list() {
		Set<String> result = new HashSet<>();
		for (BundleDescriptor descriptor : descriptors.values()) {
			result.add(descriptor.bsn);
		}
		return result;
	}

	Collection<Version> list(String bsn) {

		Set<Version> result = new HashSet<>();
		for (BundleDescriptor descriptor : descriptors.values()) {
			if (isBsn(bsn, descriptor)) {
				result.add(descriptor.version);
			}
		}
		return result;
	}

	boolean isBsn(String bsn, BundleDescriptor descriptor) {
		return bsn.equals(descriptor.bsn) || bsn.equals(descriptor.archive.getWithoutVersion());
	}

	public synchronized BundleDescriptor getDescriptor(String bsn, Version version) throws Exception {
		sync();
		for (BundleDescriptor descriptor : descriptors.values()) {
			if (isBsn(bsn, descriptor) && version.equals(descriptor.version)) {
				return descriptor;
			}
		}
		return null;
	}

	int getErrors(String name) {
		int errors = 0;
		for (BundleDescriptor bd : descriptors.values())
			if ((name == null || name.equals(bd.bsn)) && bd.error != null)
				errors++;
		return errors;
	}

	Set<Program> getProgramsForBsn(String name) {
		Set<Program> set = new HashSet<>();
		for (BundleDescriptor bd : descriptors.values())
			if (name == null || name.equals(bd.bsn)) {
				set.add(bd.archive.revision.program);
			}
		return set;
	}

	long last = 0L;

	boolean refresh() throws Exception {

		refresh.getAndSet(false);

		if (indexFile.lastModified() != lastModified && last + 10000 < System.currentTimeMillis()) {
			loadIndexFile();
			last = System.currentTimeMillis();

			return true;
		} else {
			boolean refreshed = false;
			for (BundleDescriptor bd : descriptors.values()) {
				if (bd.promise != null && bd.promise.isDone() && bd.promise.getFailure() == null) {
					File f = bd.promise.getValue();
					if (f.isFile() && f.lastModified() != bd.lastModified) {
						updateDescriptor(bd, f);
						refreshed = true;
					}
				}
			}
			if (refreshed)
				return true;
		}
		return false;
	}

	private void loadIndexFile() throws Exception {
		lastModified = indexFile.lastModified();
		Set<Archive> toBeDeleted = new HashSet<>(descriptors.keySet());
		if (indexFile.isFile()) {
			try (BufferedReader rdr = IO.reader(indexFile)) {
				String line;
				while ((line = rdr.readLine()) != null) {
					line = Strings.trim(line);
					if (line.startsWith("#") || line.isEmpty())
						continue;

					Archive a = Archive.valueOf(line);
					if (a == null) {
						reporter.error("MavenBndRepository: invalid entry %s in file %s", line, indexFile);
					} else {
						toBeDeleted.remove(a);
						loadDescriptorAsync(a);
					}
				}
			}

			this.descriptors.keySet().removeAll(toBeDeleted);
			this.promises.keySet().removeAll(toBeDeleted);
		}
	}

	void updateDescriptor(final Archive archive, File file) {
		BundleDescriptor descriptor = descriptors.get(archive);
		updateDescriptor(descriptor, file);
	}

	void updateDescriptor(BundleDescriptor descriptor, File file) {
		try {
			if (file == null || !file.isFile()) {
				File descriptorFile = getDescriptorFile(descriptor.archive);
				descriptorFile.delete();
				reporter.error("Could not find file %s", descriptor.archive);
				descriptor.error = "File not found";
			} else {
				if (descriptor.lastModified != file.lastModified()) {

					Domain m = Domain.domain(file);
					if (m == null)
						m = Domain.domain(Collections.<String, String> emptyMap());

					Entry<String,Attrs> bsn = m.getBundleSymbolicName();
					descriptor.bsn = bsn != null ? bsn.getKey() : null;
					descriptor.version = m.getBundleVersion() == null ? null
							: Version.parseVersion(m.getBundleVersion());

					if (descriptor.bsn == null) {
						descriptor.bsn = descriptor.archive.getWithoutVersion();
						descriptor.version = descriptor.archive.revision.version.getOSGiVersion();
					} else if (descriptor.version == null)
						descriptor.version = Version.LOWEST;
					descriptor.description = m.getBundleDescription();
					descriptor.id = SHA1.digest(file).digest();
					descriptor.included = false;
					descriptor.lastModified = file.lastModified();
					descriptor.sha256 = SHA256.digest(file).digest();
					saveDescriptor(descriptor);
					refresh.set(true);
				}
				if (descriptor.promise == null && file != null)
					descriptor.promise = Promises.resolved(file);
			}
		} catch (Exception e) {
			e.printStackTrace();
			descriptor.error = e.toString();
			refresh.set(true);

		}
	}

	private void saveDescriptor(BundleDescriptor descriptor) throws IOException, Exception {
		File df = getDescriptorFile(descriptor.archive);
		df.getParentFile().mkdirs();
		CODEC.enc().to(df).put(descriptor);
	}

	private void loadDescriptorAsync(final Archive archive) throws Exception {
		if (updateLocal(archive))
			return;

		updateAsync(archive);
	}

	private boolean updateLocal(final Archive archive) {
		File archiveFile = repo.toLocalFile(archive);
		if (archiveFile.isFile()) {
			File descriptorFile = getDescriptorFile(archive);
			if (descriptorFile.isFile()) {
				try {
					BundleDescriptor descriptor = CODEC.dec().from(descriptorFile).get(BundleDescriptor.class);
					descriptor.promise = Promises.resolved(archiveFile);
					descriptor.resource = null;
					descriptors.put(archive, descriptor);
					return true;
				} catch (Exception e) {
					// ignore
				}
			}
		}
		return false;
	}

	Promise<File> updateAsync(final Archive archive) throws Exception {
		Promise<File> promise = repo.get(archive);
		return updateAsync(archive, promise);
	}

	Promise<File> updateAsync(final Archive archive, Promise<File> promise) throws Exception {
		BundleDescriptor descriptor = createInitialDescriptor(archive);
		promise = updateAsync(descriptor, promise);
		descriptors.put(archive, descriptor);
		return promise;
	}

	Promise<File> updateAsync(final BundleDescriptor descriptor, Promise<File> promise) throws Exception {
		descriptor.promise = promise.map(new Function<File,File>() {

			@Override
			public File apply(File file) {
				updateDescriptor(descriptor, file);
				return file;
			}

		});
		descriptor.resource = null;
		promises.put(descriptor.archive, descriptor.promise);
		return descriptor.promise;
	}

	File getDescriptorFile(Archive archive) {
		File dir = new File(repo.toLocalFile(archive).getParentFile(), ".bnd");
		return new File(dir, archive.getName());
	}

	private BundleDescriptor createInitialDescriptor(Archive archive) throws Exception {
		BundleDescriptor descriptor = new BundleDescriptor();
		descriptor.archive = archive;
		descriptor.phase = archive.isSnapshot() ? Phase.STAGING : Phase.MASTER;
		descriptor.url = repo.toRemoteURI(archive);
		descriptor.bsn = archive.getWithoutVersion();
		descriptor.version = archive.revision.version.getOSGiVersion();
		return descriptor;
	}

	private void saveIndexFile() throws Exception {
		File tmp = File.createTempFile("index", null);
		try (PrintWriter pw = new PrintWriter(tmp);) {
			List<Archive> archives = new ArrayList<>(this.descriptors.keySet());
			Collections.sort(archives);
			for (Archive archive : archives) {
				pw.println(archive);
			}
		}
		Files.move(tmp.toPath(), indexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

		lastModified = indexFile.lastModified();
	}

	public void save() throws Exception {
		saveIndexFile();
	}

	public Collection<Archive> getArchives() {
		return descriptors.keySet();
	}

	@SuppressWarnings({
			"unchecked", "rawtypes"
	})
	public Map<Requirement,Collection<Capability>> findProviders(Collection< ? extends Requirement> requirements) {
		MultiMap<Requirement,Capability> providers = new MultiMap<>();
		for (BundleDescriptor bd : descriptors.values()) {
			Resource r = bd.getResource();
			if (r != null) {
				for (Requirement req : requirements) {
					for (Capability cap : r.getCapabilities(req.getNamespace())) {
						if (ResourceUtils.matches(req, cap))
							providers.add(req, cap);
					}
				}
			}
		}
		return (Map) providers;
	}

}
