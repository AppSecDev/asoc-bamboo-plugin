/**
 * (c) Copyright IBM Corporation 2016.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.ibm.appscan.bamboo.plugin.impl;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Hashtable;

import org.apache.tools.ant.types.FileSet;

import com.atlassian.bamboo.build.artifact.ArtifactHandlingUtils;
import com.atlassian.bamboo.build.artifact.ArtifactManager;
import com.atlassian.bamboo.configuration.ConfigurationMap;
import com.atlassian.bamboo.credentials.CredentialsData;
import com.atlassian.bamboo.credentials.CredentialsManager;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionContext;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionContextImpl;
import com.atlassian.bamboo.plan.artifact.ArtifactPublishingResult;
import com.atlassian.bamboo.process.ProcessService;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.task.TaskContext;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.task.TaskResult;
import com.atlassian.bamboo.task.TaskResultBuilder;
import com.atlassian.bamboo.task.TaskType;
import com.atlassian.bamboo.utils.i18n.I18nBeanFactory;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.core.util.FileUtils;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

@Scanned
public class SASTScanTask implements TaskType, ISASTConstants, IArtifactPublisher {
	
	private static final String SA_DIR 		= ".sa";			//$NON-NLS-1$
	private static final long TIME_TO_SLEEP	= 5 * 60 * 1000;	// 5 minutes
	
	private LogHelper logger;
	private SASTScanner scanner;
	
	private ArtifactManager artifactManager;
	private CredentialsManager credentialsManager;
	private EncryptionService encryptionService;
	private CapabilityContext capabilityContext;
	
	public SASTScanTask(
			@ComponentImport I18nBeanFactory i18nBeanFactory, 
			@ComponentImport ProcessService processService, 
			@ComponentImport ArtifactManager artifactManager, 
			@ComponentImport CredentialsManager credentialsManager, 
			@ComponentImport EncryptionService encryptionService, 
			@ComponentImport CapabilityContext capabilityContext) {
		
		logger = new LogHelper(i18nBeanFactory.getI18nBean());
		scanner = new SASTScanner(logger, processService);
		
		this.artifactManager = artifactManager;
		this.credentialsManager = credentialsManager;
		this.encryptionService = encryptionService;
		this.capabilityContext = capabilityContext;
	}
	
	@Override
	public void publishArtifact(TaskContext taskContext, String name, File directory, String pattern) {
		
		logger.info("publish.artifact", name); //$NON-NLS-1$
		
		ArtifactDefinitionContext artifact = new ArtifactDefinitionContextImpl(name, true, null);
		artifact.setCopyPattern(pattern);
		
		ArtifactPublishingResult result = artifactManager.publish(
				taskContext.getBuildLogger(), 
				taskContext.getBuildContext().getPlanResultKey(), 
				directory, 
				artifact, 
				new Hashtable<String, String>(), 
				1);
		
		taskContext.getBuildContext().getArtifactContext().addPublishingResult(result);
	}
	
	private void setUsernameAndPasswordVars(TaskContext taskContext) {
		
		String id = taskContext.getConfigurationMap().get(CFG_SELECTED_CRED);
		CredentialsData credentials = credentialsManager.getCredentials(Long.parseLong(id));
		
		String username = credentials.getConfiguration().get("username"); //$NON-NLS-1$
		taskContext.getBuildContext().getVariableContext().addLocalVariable(ASOC_USERNAME, username);
		
		String password = credentials.getConfiguration().get("password"); //$NON-NLS-1$
		taskContext.getBuildContext().getVariableContext().addLocalVariable(ASOC_PASSWORD, encryptionService.decrypt(password));
	}
	
	private File copyArtifacts(TaskContext taskContext) throws TaskException {
		
		File workingDir = taskContext.getWorkingDirectory();
		File dirToScan = new File(workingDir, SA_DIR);
		
		if (dirToScan.exists())
			FileUtils.deleteDir(dirToScan);
		
		dirToScan.mkdirs();
		
		Collection<ArtifactDefinitionContext> artifacts = taskContext.getBuildContext().getArtifactContext().getDefinitionContexts();
		
		if (artifacts.isEmpty())
			throw new TaskException(logger.getText("err.no.artifacts")); //$NON-NLS-1$
		
		try {
			for (ArtifactDefinitionContext artifact : artifacts) {
				logger.info("copy.artifact", artifact.getName(), dirToScan); //$NON-NLS-1$
				FileSet fileSet = ArtifactHandlingUtils.createFileSet(workingDir, artifact, true, null);
				ArtifactHandlingUtils.copyFileSet(fileSet, dirToScan);
			}
			
			return dirToScan;
		}
		catch (IOException e) {
			throw new TaskException(e.getLocalizedMessage(), e);
		}
	}
	
	private String getUtilPath(TaskContext taskContext) {
		
		String selectedUtil = taskContext.getConfigurationMap().get(CFG_SELECTED_UTIL);
		String utilPath = capabilityContext.getCapabilityValue(
				SYS_BUILDER_PREFIX + SA_CLIENT_UTIL_KEY + '.' + selectedUtil);
		
		logger.info("util.info", selectedUtil, utilPath); //$NON-NLS-1$
		
		return utilPath;
	}
	
	private boolean checkFail(ConfigurationMap config, String key, long actual) {
		
		String value = config.get(key);
		
		if (value == null || value.equals("")) {	//$NON-NLS-1$
			logger.info(key + ".none");				//$NON-NLS-1$
			return false;
		}
		
		long limit = Long.parseLong(value);
		
		if (actual > limit) {
			logger.error(key + ".fail", actual, limit); //$NON-NLS-1$
			return true;
		}
		
		logger.info(key + ".pass", actual, limit); //$NON-NLS-1$
		return false;
	}
	
	private TaskResultBuilder calculateResult(TaskContext taskContext, TaskResultBuilder result) {
		
		ConfigurationMap config = taskContext.getConfigurationMap();
		
		boolean failed = checkFail(config, CFG_MAX_HIGH, scanner.getHighCount());
		failed |= checkFail(config, CFG_MAX_MEDIUM, scanner.getMediumCount()); 
		failed |= checkFail(config, CFG_MAX_LOW, scanner.getLowCount());
		
		if (failed)
			return result.failed();
		
		return result.success();
	}
	
	@Override
	public TaskResult execute(TaskContext taskContext) throws TaskException {
		
		logger.setLogger(taskContext.getBuildLogger());
		
		setUsernameAndPasswordVars(taskContext);
		
		scanner.setWorkingDir(copyArtifacts(taskContext));
		scanner.setUtilPath(getUtilPath(taskContext));
		
		scanner.generateIRX(taskContext, this);
		scanner.submitIRX(taskContext);
		
		TaskResultBuilder result = TaskResultBuilder.newBuilder(taskContext);		
		
		try {
			do {
				Thread.sleep(TIME_TO_SLEEP);
			}
			while (!scanner.isReady(taskContext));
			
			scanner.downloadResult(taskContext, this);
			
			return calculateResult(taskContext, result).build();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return result.failedWithError().build();
		}
	}
}