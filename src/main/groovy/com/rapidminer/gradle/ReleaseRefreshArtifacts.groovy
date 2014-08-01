/*
 * Copyright 2013-2014 RapidMiner GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.rapidminer.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.TaskAction

/**
 * This task is used to refresh artifacts published via the maven-publish 
 * mechanism after running a task which changes the project version during 
 * runtime (e.g. like 'releasePrepare').
 *
 * @author Nils Woehler
 *
 */
class ReleaseRefreshArtifacts extends DefaultTask {

	String releaseRepoUrl
	String snapshotRepoUrl

	@TaskAction
	def refreshArtifacts() {
		// Refresh all Maven publishing tasks
		project.tasks.withType(PublishToMavenRepository) { publishTask ->

			// First remember and remove old artifacts
			def oldArtifacts = publishTask.publication.artifacts.toArray()
			publishTask.publication.artifacts.clear()

			// Update publish task with new artifacts with correct source
			oldArtifacts.each({ artifact ->
				def newPath = artifact.file.absolutePath.replaceAll(publishTask.publication.version, project.version)
				publishTask.publication.artifacts.artifact(
						source:      	newPath,
						classifier:  	artifact.classifier,
						extension:  	artifact.extension
						)
			})
			publishTask.publication.version = project.version

			// If repository URL contains release or snapshot repository
			def snapshotURI = new URI(getSnapshotRepoUrl())
			def releaseURI = new URI(getReleaseRepoUrl())
			if(publishTask.repository.url.equals(releaseURI) || publishTask.repository.url.equals(snapshotURI)) {
				// adapt URL according to current version
				if(project.version.endsWith(ReleaseHelper.SNAPSHOT)) {
					publishTask.repository.url = snapshotURI
				} else {
					publishTask.repository.url = releaseURI
				}
			}
		}
		// Also update POM generation tasks with current version
		project.tasks.withType(GenerateMavenPom).each { generateMavenPomTask ->
			generateMavenPomTask.pom.getProjectIdentity().version = project.version
		}
	}
}