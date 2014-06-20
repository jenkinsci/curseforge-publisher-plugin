package org.jenkinsci.plugins.CursePublish;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

import javax.servlet.ServletException;

import jenkins.util.VirtualFile;
import ksp.curse.Uploader;
import ksp.curse.Uploader.ReleaseType;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class CursePublisher extends Publisher {
	private final String versions;
	private final String release;
	private final String changelog;
	private final String modId;
	private final String apiKey;
	private final String game;
	private final String file;

	@DataBoundConstructor
	public CursePublisher(String game, String apiKey, String modId,
			String changelog, String release, String versions, String file) {
		this.game = game;
		this.apiKey = apiKey;
		this.modId = modId;
		this.changelog = changelog;
		this.release = release;
		this.versions = versions;
		this.file = file;
	}

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) {
		if (build.getResult().isWorseOrEqualTo(Result.FAILURE))
			return true;

		try {
			VirtualFile file = build.getWorkspace().child(this.file)
					.toVirtualFile();
			if (!file.exists() || !file.canRead()) {
				listener.getLogger().println("Could not find " + this.file);
				return false;
			}

			Uploader uploader = new Uploader();
			long fileId = uploader.uploadMod(check(build, game),
					check(build, apiKey), Long.parseLong(check(build, modId)),
					file.getName(), file.open(), check(build, changelog),
					ReleaseType.valueOf(check(build, release).toUpperCase()),
					check(build, versions).split(","));
			listener.getLogger().println(
					"Published " + this.file + " to " + check(build, game)
							+ ".curseforge.com with file id " + fileId);
		} catch (Exception e) {
			e.printStackTrace(listener.getLogger());
			return false;
		}

		return true;
	}

	private String check(AbstractBuild build, String val) throws IOException {
		if (!val.startsWith("@"))
			return val;

		VirtualFile file = build.getWorkspace().child(val.substring(1))
				.toVirtualFile();
		if (!file.exists() || !file.canRead()) {
			throw new FileNotFoundException("Could not find "
					+ val.substring(1));
		}

		Scanner scanner = new Scanner(file.open()).useDelimiter("\\A");
		try {
			return scanner.next();
		} finally {
			scanner.close();
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Publisher> {

		public DescriptorImpl() {
			load();
		}

		public FormValidation doCheckApiKey(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set an API Key");
			return FormValidation.ok();
		}

		public FormValidation doCheckFile(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set a file to upload");
			return FormValidation.ok();
		}

		public FormValidation doCheckGame(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set a game to upload for");
			return FormValidation.ok();
		}

		public FormValidation doCheckModId(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation.error("Please set a Mod ID");
			try {
				Long l = Long.parseLong(value);
			} catch (NumberFormatException ex) {
				return FormValidation
						.error("Mod IDs are usually positive numbers");
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckVersions(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0)
				return FormValidation
						.error("Please configure at least one version of the game your mod works with");
			return FormValidation.ok();
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		public String getDisplayName() {
			return "Publish to Curseforge";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {
			save();
			return super.configure(req, formData);
		}
	}

	public String getVersions() {
		return versions;
	}

	public String getRelease() {
		return release;
	}

	public String getChangelog() {
		return changelog;
	}

	public String getModId() {
		return modId;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getGame() {
		return game;
	}

	public String getFile() {
		return file;
	}

}
