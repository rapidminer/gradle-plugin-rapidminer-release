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

import org.ajoberstar.grgit.*
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

/**
 * The release preparation task adapts the project's version and 
 * merges the release branch to develop.
 * 
 * @author Nils Woehler
 *
 */
class PrepareRelease extends DefaultTask {

	def GitScmProvider scmProvider
	def String releaseBranch

	// Variables below will be defined by the conventionalMapping
	def String masterBranch
	def	boolean pushToRemote
	def Closure generateTagName

	@TaskAction
	def prepareRelease() {

		/*
		 * 1. Ensure we are not on master branch 
		 */
		if(releaseBranch.equals(getMasterBranch())){
			throw new GradleException("Cannot prepare release. Release branch is master branch '${getMasterBranch()}'!.")
		}

		/*
		 * 2. Ensuring all changes in the repository have been committed.
		 */
		scmProvider.ensureNoUncommittedChanges()

		/*
		 * 3. Ensuring there aren't any commits in the upstream branch that haven't been merged yet.
		 */
		scmProvider.ensureNoUpstreamChanges()

		/*
		 * 4. Look for 'gradle.properties' in root project and load properties
		 */
		def gradleProperties = ReleaseHelper.getGradleProperties(project)
		if(!gradleProperties.version){
			throw new GradleException("Could not find 'version' property in root project's 'gradle.properties' file.")
		}

		/*
		 * 5. Ask user for release version number 
		 */
		def releaseVersion = gradleProperties.version.replace(ReleaseHelper.SNAPSHOT, '')
		if(project.hasProperty(ReleaseHelper.PROPERTY_RELEASE_VERSION)) {
			releaseVersion = project.properties[ReleaseHelper.PROPERTY_RELEASE_VERSION]
		}

		// perform a version format check
		if(!(releaseVersion ==~ /[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}/)) {
			throw new GradleException("Release version is of wrong format. Correct format example: '1.0.003'.")
		}

		logger.info("Current branch is: ${releaseBranch}")
		logger.info("Release version is ${releaseVersion}")

		def interactive = true
		if(project.hasProperty(ReleaseHelper.PROPERTY_RELEASE_INTERACTIVE)) {
			interactive = Boolean.valueOf(project.properties[ReleaseHelper.PROPERTY_RELEASE_INTERACTIVE])
		}
		if(interactive) {
			releaseVersion = askForReleaseVersion(releaseVersion)
			verifyReleaseInput(releaseBranch, releaseVersion)
		}

		/*
		 * Change project version to release version
		 * (Otherwise publishing to e.g. a Maven repository would 
		 * still show the old version).
		 * 
		 * Due to the change of the project version the 
		 * 'releaseRefreshArtifacts' tasks has to be run afterwards.
		 */
		project.version = releaseVersion

		/*
		 * 6. Change version in 'gradle.properties' to release version
		 */
		logger.info("Changing 'gradle.properties' to new version '${releaseVersion}'.")
		gradleProperties.version = releaseVersion
		gradleProperties.store(ReleaseHelper.getGradlePropertiesFile(project).newWriter(), null)
		scmProvider.commit("Prepare 'gradle.properties' for release of version ${releaseVersion}", [
			ReleaseHelper.GRADLE_PROPERTIES] as List)

		/*
		 * 7. Switch to '${masterBranch}'
		 */
		scmProvider.switchToBranch(getMasterBranch())

		/*
		 * 8. Ensuring there aren't any commits in the upstream branch that haven't been merged yet.
		 */
		scmProvider.ensureNoUpstreamChanges()
		
		/*
		 * 8a. Ensure the current version isn't already tagged
		 */
		scmProvider.ensureNoTag(ReleaseHelper.execClosure(project.version, getGenerateTagName()))

		/*
		 * 9. Merge release branch into 'master'
		 */
		scmProvider.merge(releaseBranch)

		/*
		 * 10. Push changes to remote repository
		 */
		if(isPushToRemote()) {
			scmProvider.push([getMasterBranch()] as List, false)
		}
	}

	/**
	 * Asks the user for the next version to be released. By default it will be the version 
	 * defined in gradle.properties without the SNAPSHOT tag. But the user is also able to 
	 * change the version (e.g. for hotfix releases).
	 * 
	 * @param currentVersion the current version written in gradle.properties
	 * @return the version that will be released
	 */
	def String askForReleaseVersion(releaseVersion){
		ReleaseHelper.println "Next release version is: ${releaseVersion}"

		// ask for new version number
		def correctInput = false
		def iterations = 0
		while(!correctInput) {
			def changeVersion = ReleaseHelper.readLine("Is release version '${releaseVersion}' correct?", "yes")
			if(changeVersion){
				if(changeVersion.toLowerCase().startsWith("n")){
					while(!correctInput){
						releaseVersion = ReleaseHelper.readLine('Please enter new release version: ')

						// check whether specified version is a valid version number
						correctInput = (releaseVersion ==~ /[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}/)
						if(!correctInput){
							ReleaseHelper.println "Invalid release version number: '${releaseVersion}'. A version must contain a major version (up to three digits), a minor version (up to three digits), and a patch level (up to three digits) separated by dots (e.g. 1.0.003)."
						}
					}
				} else if(!changeVersion.toLowerCase().startsWith("y")){
					ReleaseHelper.println "Undefined input. Please type 'yes' or 'no'!"
				} else {
					correctInput = true
				}
			} else {
				correctInput = true
			}
		}
		return releaseVersion
	}

	/**
	 * Lets the user verify that the release preparation configuration is correct.
	 * 
	 * @param releaseBranch the current branch
	 * @param releaseVersion the version to release
	 */
	def verifyReleaseInput(releaseBranch, releaseVersion) {

		ReleaseHelper.println "Please verify release preparation configuration"
		ReleaseHelper.println "-----------------------------------------------------"
		ReleaseHelper.println "Current branch: '${releaseBranch}'"
		ReleaseHelper.println "Version to release: '${releaseVersion}'"
		ReleaseHelper.println "Next actions:"
		ReleaseHelper.println "* Merge '${releaseBranch}' to '${getMasterBranch()}'"
		ReleaseHelper.println "-----------------------------------------------------"
		ReleaseHelper.println ""

		def i = 0
		while(i++ <= 2){
			Random random = new Random()
			int a = random.nextInt(10) + 1
			int b = random.nextInt(10) + 1
			def correctResult = a + b
			def result = ReleaseHelper.readLine("Please acknowledge: ${a} + ${b} = ")
			try {
				if(result.toInteger() != correctResult) {
					ReleaseHelper.println "Wrong result (${a} + ${b} = ${correctResult})! Please try again..."
				} else {
					ReleaseHelper.println "Release preparation configuration acknowledged! Preparing release..."
					return
				}
			} catch(ex) {
				ReleaseHelper.println "Something went wrong. Please try again."
			}
		}
		throw new GradleException("Aborting after trying three times to acknowledge release preparation configuration.")
	}
}