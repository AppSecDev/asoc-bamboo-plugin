/**
 * (c) Copyright IBM Corporation 2016.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.ibm.appscan.bamboo.plugin.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
	
	private static final String APPSCAN_OPTS		= "APPSCAN_OPTS";		//$NON-NLS-1$
	private static final String APPSCAN_INTERVAL	= "APPSCAN_INTERVAL";	//$NON-NLS-1$
	
	private static final String STATUS_FAILED		= "Failed";				//$NON-NLS-1$
	private static final String STATUS_READY		= "Ready";				//$NON-NLS-1$
	
	private static final int MIN_TIME_TO_SLEEP		= 30;
	private static final int TIME_TO_SLEEP			= 120;
	
	private LogHelper logger;
	private ProcessService processService;
	
	private String username;
	private String password;
	
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
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public void setPassword(String password) {
		this.password = password;
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
	
	private String getVariableValue(TaskContext taskContext, String name) {
		VariableContext variables = taskContext.getBuildContext().getVariableContext();
		VariableDefinitionContext variable = variables.getEffectiveVariables().get(name);
		return variable == null ? null : variable.getValue();
	}
	
	private ExternalProcessBuilder createExternalProcessBuilder(TaskContext taskContext, String... commands) {
		
		ExternalProcessBuilder builder = new ExternalProcessBuilder();
		builder.workingDirectory(workingDir);
		
		String appscanOpts = getVariableValue(taskContext, APPSCAN_OPTS);
		if (appscanOpts != null)
			builder.env(APPSCAN_OPTS, appscanOpts);
		
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
						"prepare", 		//$NON-NLS-1$
						"-n", irxBaseName));	//$NON-NLS-1$
		
		publisher.publishArtifact(taskContext, "IRX", workingDir, irxBaseName + "*.*"); //$NON-NLS-1$ //$NON-NLS-2$
		
		int exitCode = process.getHandler().getExitCode();
		if (exitCode != 0)
			throw new TaskException(logger.getText("generate.irx.failed", exitCode)); //$NON-NLS-1$
	}
	
	private String getLastLogEntry(TaskContext taskContext) {
		
		List<LogEntry> logs = taskContext.getBuildLogger().getLastNLogEntries(2);
		Collections.reverse(logs);
		
		String text = ""; //$NON-NLS-1$
		
		for (LogEntry log : logs) {
			text = log.getUnstyledLog();
			if (!text.trim().isEmpty())
				break;
		}
		
		return text;
	}
	
	private void loginToASoC(TaskContext taskContext) {
		processService.executeExternalProcess(
				taskContext, 
				createExternalProcessBuilder(
						taskContext,
						"scx_login",		//$NON-NLS-1$
						"-u", username,		//$NON-NLS-1$
						"-P", password));	//$NON-NLS-1$
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
		
		jobId = getLastLogEntry(taskContext);
		if (!jobId.matches("^[-0-9a-zA-Z]+$"))								//$NON-NLS-1$
			throw new TaskException(logger.getText("submit.irx.failed2"));	//$NON-NLS-1$
	}
	
	private int getTimeToSleep(TaskContext taskContext) {
		
		String value = getVariableValue(taskContext, APPSCAN_INTERVAL);
		if (value != null) {
			try {
				return Math.max(Integer.parseInt(value), MIN_TIME_TO_SLEEP);
			}
			catch (NumberFormatException e) {
				// fall through
			}
		}
		
		return TIME_TO_SLEEP;
	}
	
	private JsonNode pollForStatus(TaskContext taskContext) {
		
		processService.executeExternalProcess(
				taskContext, 
				createExternalProcessBuilder(
						taskContext,
						"info",			//$NON-NLS-1$
						"-i", jobId,	//$NON-NLS-1$
						"-json"));		//$NON-NLS-1$
		
		try {
			return new ObjectMapper().readTree(getLastLogEntry(taskContext));
		}
		catch (IOException e) {
			return null;
		}
	}
	
	private boolean isReady(TaskContext taskContext) throws TaskException {
		
		logger.info("status.check"); //$NON-NLS-1$
		
		JsonNode response = pollForStatus(taskContext);
		if (response == null) {
			loginToASoC(taskContext);
			response = pollForStatus(taskContext);
			if (response == null)
				throw new TaskException(logger.getText("status.check.failed")); //$NON-NLS-1$
		}
		
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
		
		throw new TaskException(logger.getText("scan.failed")); //$NON-NLS-1$
	}
	
	public void waitForReady(TaskContext taskContext) throws TaskException, InterruptedException {
		do {
			Thread.sleep(getTimeToSleep(taskContext) * 1000L);
		}
		while (!isReady(taskContext));
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
