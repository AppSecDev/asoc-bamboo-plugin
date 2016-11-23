/**
 * (c) Copyright IBM Corporation 2016.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.ibm.appscan.bamboo.plugin.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;

import com.atlassian.bamboo.build.LogEntry;
import com.atlassian.bamboo.process.ExternalProcessBuilder;
import com.atlassian.bamboo.process.ProcessService;
import com.atlassian.bamboo.task.TaskContext;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.variable.VariableContext;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.atlassian.utils.process.ExternalProcess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SASTScanner implements ISASTConstants, IJSONConstants {
	
	private static final String APPSCAN_OPTS	= "APPSCAN_OPTS";	//$NON-NLS-1$
	private static final String RESPONSE_JSON	= "response.json";	//$NON-NLS-1$
	private static final String STATUS_FAILED	= "Failed";			//$NON-NLS-1$
	private static final String STATUS_READY	= "Ready";			//$NON-NLS-1$
	
	private LogHelper logger;
	private ProcessService processService;
	
	private File workingDir;
	private String utilPath;
	
	private String irxBaseName;
	private String jobId;
	
	private long high;
	private long medium;
	private long low;
	
	public SASTScanner(LogHelper logger, ProcessService processService) {
		this.logger = logger;
		this.processService = processService;
	}
	
	public void setWorkingDir(File workingDir) {
		this.workingDir = workingDir;
	}
	
	public void setUtilPath(String utilPath) {
		if (SystemUtils.IS_OS_WINDOWS)
			this.utilPath = utilPath + "\\bin\\appscan.bat";	//$NON-NLS-1$
		else
			this.utilPath = utilPath + "/bin/appscan.sh";		//$NON-NLS-1$
	}
	
	private ExternalProcessBuilder createExternalProcessBuilder(TaskContext taskContext, String... commands) {
		
		ExternalProcessBuilder builder = new ExternalProcessBuilder();
		builder.workingDirectory(workingDir);
		
		VariableContext variables = taskContext.getBuildContext().getVariableContext();
		VariableDefinitionContext variable = variables.getEffectiveVariables().get(APPSCAN_OPTS);
		if (variable != null)
			builder.env(APPSCAN_OPTS, variable.getValue());
		
		List<String> list = new ArrayList<String>();
		list.add(utilPath);
		list.addAll(Arrays.asList(commands));
		builder.command(list);
		
		return builder;
	}
	
	public void generateIRX(TaskContext taskContext, IArtifactPublisher publisher) throws TaskException {
		
		irxBaseName = taskContext.getBuildContext().getBuildResultKey();
		
		logger.info("generate.irx", irxBaseName, workingDir); //$NON-NLS-1$
		
		ExternalProcess process = processService.executeExternalProcess(
				taskContext, 
				createExternalProcessBuilder(
						taskContext, 
						"prepare", 				//$NON-NLS-1$
						"-n", irxBaseName));	//$NON-NLS-1$
		
		publisher.publishArtifact(taskContext, "IRX", workingDir, irxBaseName + "*.*"); //$NON-NLS-1$ //$NON-NLS-2$
		
		int exitCode = process.getHandler().getExitCode();
		if (exitCode != 0)
			throw new TaskException(logger.getText("generate.irx.failed", exitCode)); //$NON-NLS-1$
	}
	
	private String envVar(String value) {
		return SystemUtils.IS_OS_WINDOWS ? 
				"%bamboo_" + value + "%" : "$bamboo_" + value; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
	
	private void loginToASoC(TaskContext taskContext) {
		processService.executeExternalProcess(
				taskContext, 
				createExternalProcessBuilder(
						taskContext,
						"scx_login",  					//$NON-NLS-1$
						"-u", envVar(ASOC_USERNAME),	//$NON-NLS-1$
						"-P", envVar(ASOC_PASSWORD)));	//$NON-NLS-1$
	}
	
	public void submitIRX(TaskContext taskContext) throws TaskException {
		
		logger.info("submit.irx"); //$NON-NLS-1$
		
		loginToASoC(taskContext);
		
		String appId = taskContext.getConfigurationMap().get(CFG_APP_ID);
		
		ExternalProcess process = processService.executeExternalProcess(
				taskContext, 
				createExternalProcessBuilder(
						taskContext,
						"queue_analysis",  				//$NON-NLS-1$
						"-a", appId,					//$NON-NLS-1$
						"-n", irxBaseName + ".irx"));	//$NON-NLS-1$ //$NON-NLS-2$
		
		int exitCode = process.getHandler().getExitCode();
		if (exitCode != 0)
			throw new TaskException(logger.getText("submit.irx.failed", exitCode)); //$NON-NLS-1$
		
		List<LogEntry> logs = taskContext.getBuildLogger().getLastNLogEntries(1);
		jobId = logs.get(0).getUnstyledLog();
	}
	
	private int pollForStatus(TaskContext taskContext) {
		
		ExternalProcess process = processService.executeExternalProcess(
				taskContext, 
				createExternalProcessBuilder(
						taskContext,
						"info",  				//$NON-NLS-1$
						"-i", jobId,			//$NON-NLS-1$
						"-json", 				//$NON-NLS-1$
						">", RESPONSE_JSON));	//$NON-NLS-1$
		
		return process.getHandler().getExitCode();
	}
	
	public boolean isReady(TaskContext taskContext) throws TaskException {
		
		logger.info("status.check"); //$NON-NLS-1$
		
		int exitCode = pollForStatus(taskContext);
		if (exitCode != 0) {
			loginToASoC(taskContext);
			exitCode = pollForStatus(taskContext);
			if (exitCode != 0)
				throw new TaskException(logger.getText("status.check.failed", exitCode)); //$NON-NLS-1$
		}
		
		try {
			JsonNode response = new ObjectMapper().readTree(new File(workingDir, RESPONSE_JSON));
			
			if (response != null) {
				
				String status = response.at(STATUS).asText(STATUS_FAILED);
				logger.info("status.check.is", status); //$NON-NLS-1$
				
				if (!STATUS_FAILED.equals(status)) {
					
					if (!STATUS_READY.equals(status))
						return false;
					
					high = response.at(HIGH).asLong(-1);
					medium = response.at(MEDIUM).asLong(-1);
					low = response.at(LOW).asLong(-1);
					
					return true;
				}
			}
			
			throw new TaskException(logger.getText("scan.failed")); //$NON-NLS-1$
		}
		catch (IOException e) {
			throw new TaskException(e.getLocalizedMessage(), e);
		}
	}
	
	public void downloadResult(TaskContext taskContext, IArtifactPublisher publisher) throws TaskException {
		
		logger.info("download.result"); //$NON-NLS-1$
		
		String html = irxBaseName + ".html"; //$NON-NLS-1$
		
		ExternalProcess process = processService.executeExternalProcess(
				taskContext, 
				createExternalProcessBuilder(
						taskContext,
						"get_result",  	//$NON-NLS-1$
						"-i", jobId,	//$NON-NLS-1$
						"-d", html));	//$NON-NLS-1$
		
		publisher.publishArtifact(taskContext, logger.getText("result.artifact"), workingDir, html); //$NON-NLS-1$
		
		int exitCode = process.getHandler().getExitCode();
		if (exitCode != 0)
			throw new TaskException(logger.getText("download.result.failed", exitCode)); //$NON-NLS-1$
	}
	
	public long getHighCount() {
		return high;
	}
	
	public long getMediumCount() {
		return medium;
	}
	
	public long getLowCount() {
		return low;
	}
}