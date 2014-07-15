package com.rapidminer.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.ajoberstar.grgit.*



/**
 *
 * @author Nils Woehler
 *
 */
class RapidMinerReleasePlugin implements Plugin<Project> {

	protected static final String TASK_GROUP = "RapidMiner Release"
	protected static final String CHECK_FOR_ILLEGAL_DEPENDENCIES_NAME = "releaseCheckDependencies"
	protected static final String PREPARE_TASK_NAME = "releasePrepare"
	protected static final String FINALIZE_TASK_NAME = "releaseFinalize"
	protected static final String RELEASE_TASK_NAME = "release"


	@Override
	void apply(Project project) {
		Grgit grgit = Grgit.open(project.file('.'))

		def releaseBranch = grgit.branch.current.name

		RapidMinerReleaseExtension extension = project.extensions.create('release', RapidMinerReleaseExtension)
		addFinalizeTask(project, extension, grgit, releaseBranch)
		addCheckForIllegalDependencies(project, extension)
		addPrepareTask(project, extension, grgit, releaseBranch)
		addReleaseTask(project, extension, grgit)
	}

	def addCheckForIllegalDependencies(Project project, RapidMinerReleaseExtension extension) {
		project.tasks.create(name: CHECK_FOR_ILLEGAL_DEPENDENCIES_NAME, type: CheckForIllegalDependenciesTask){
			description = 'Ensures that no illegal release dependency is referenced by the project.'
			group = TASK_GROUP
		}
	}

	def addPrepareTask(Project project, RapidMinerReleaseExtension extension, Grgit gr, String currentBranch) {
		project.tasks.create(name : PREPARE_TASK_NAME, type: PrepareReleaseTask){
			description = 'Ensures the project is ready to be released.'
			group = TASK_GROUP
			if(!extension.skipIllegalDependenciesCheck) {
				dependsOn CHECK_FOR_ILLEGAL_DEPENDENCIES_NAME
			}
			grgit = gr
			releaseBranch = currentBranch
			remote = extension.remote
			masterBranch = extension.masterBranch
			createTag = extension.createTag
			generateTagName = extension.generateTagName
			generateTagMessage = extension.generateTagMessage
		}
	}

	def addFinalizeTask(Project project, RapidMinerReleaseExtension extension, Grgit gr, String relBranch) {
		project.tasks.create(name : FINALIZE_TASK_NAME, type: FinalizeReleaseTask){
			description = 'Finalizes the release by merging changes from release branch back to develop and deletes the release branch (if configured).'
			group = TASK_GROUP

			grgit = gr
			releaseBranch = relBranch
			remote = extension.remote
			masterBranch = extension.masterBranch
			mergeToDevelop = extension.mergeToDevelop
			pushChangesToRemote = extension.pushChangesToRemote
			deleteReleaseBranch = extension.deleteReleaseBranch
			pushTags = extension.createTag
		}
	}

	def addReleaseTask(Project project, RapidMinerReleaseExtension extension, Grgit grgit) {
		project.tasks.create(name : RELEASE_TASK_NAME){
			description = 'Releases the project by first preparing a release and than invoking the actual release tasks.'
			group = TASK_GROUP
			dependsOn PREPARE_TASK_NAME
			dependsOn { extension.releaseTasks }
			extension.releaseTasks.each { task -> task.mustRunAfter PREPARE_TASK_NAME }

			finalizedBy FINALIZE_TASK_NAME
		}
	}
}
