/**
 * (c) Copyright IBM Corporation 2016.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.ibm.appscan.bamboo.plugin.impl;

import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.credentials.CredentialsData;
import com.atlassian.bamboo.credentials.CredentialsManager;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.TaskRequirementSupport;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.i18n.I18nBean;
import com.atlassian.bamboo.utils.i18n.I18nBeanFactory;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.capability.RequirementImpl;
import com.atlassian.bamboo.ww2.actions.build.admin.create.UIConfigSupport;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;

@Scanned
public class SASTScanTaskConfigurator extends AbstractTaskConfigurator implements TaskRequirementSupport, ISASTConstants {
	
	private static final String UTIL_LIST = "utilList";	//$NON-NLS-1$
	private static final String CRED_LIST = "credList"; //$NON-NLS-1$
	
	private UIConfigSupport uiConfigSupport;
	private Map<Long, String> credentials;
	private I18nBean i18nBean;
	
	public SASTScanTaskConfigurator(
			@ComponentImport UIConfigSupport uiConfigSupport, 
			@ComponentImport CredentialsManager credentialsManager, 
			@ComponentImport I18nBeanFactory i18nBeanFactory) {
		
		this.uiConfigSupport = uiConfigSupport;
		
		credentials = new Hashtable<Long, String>();
		for (CredentialsData data : credentialsManager.getAllCredentials())
			credentials.put(data.getId(), data.getName());
		
		this.i18nBean = i18nBeanFactory.getI18nBean();
	}
	
	@Override
	public void populateContextForCreate(Map<String, Object> context) {
		context.put(UTIL_LIST, uiConfigSupport.getExecutableLabels(SA_CLIENT_UTIL_KEY));
		context.put(CRED_LIST, credentials);
	}
	
	@Override
	public void populateContextForEdit(Map<String, Object> context, TaskDefinition taskDefinition) {
		context.put(UTIL_LIST, uiConfigSupport.getExecutableLabels(SA_CLIENT_UTIL_KEY));
		context.put(CRED_LIST, credentials);
		Map<String, String> config = taskDefinition.getConfiguration();
		context.put(CFG_SELECTED_UTIL, config.get(CFG_SELECTED_UTIL));
		context.put(CFG_SELECTED_CRED, config.get(CFG_SELECTED_CRED));
		context.put(CFG_APP_ID, config.get(CFG_APP_ID));
		context.put(CFG_MAX_HIGH, config.get(CFG_MAX_HIGH));
		context.put(CFG_MAX_MEDIUM, config.get(CFG_MAX_MEDIUM));
		context.put(CFG_MAX_LOW, config.get(CFG_MAX_LOW));
	}
	
	private void validateRequired(ActionParametersMap params, ErrorCollection errorCollection, String field) {
		String value = params.getString(field);
		if (value == null || value.trim().isEmpty())
			errorCollection.addError(field, i18nBean.getText("err." + field)); //$NON-NLS-1$
	}
	
	private void validateNumber(ActionParametersMap params, ErrorCollection errorCollection, String field) {
		String value = params.getString(field);
		if (!("".equals(value) || StringUtils.isNumeric(value)))			//$NON-NLS-1$
			errorCollection.addError(field, i18nBean.getText("err.nan"));	//$NON-NLS-1$
	}
	
	@Override
	public void validate(ActionParametersMap params, ErrorCollection errorCollection) {
		validateRequired(params, errorCollection, CFG_SELECTED_UTIL);
		validateRequired(params, errorCollection, CFG_SELECTED_CRED);
		validateRequired(params, errorCollection, CFG_APP_ID);
		validateNumber(params, errorCollection, CFG_MAX_HIGH);
		validateNumber(params, errorCollection, CFG_MAX_MEDIUM);
		validateNumber(params, errorCollection, CFG_MAX_LOW);
	}
	
	@Override
	public Map<String, String> generateTaskConfigMap(ActionParametersMap params, TaskDefinition previousTaskDefinition) {
		Map<String, String> config = super.generateTaskConfigMap(params, previousTaskDefinition);
		config.put(CFG_SELECTED_UTIL, params.getString(CFG_SELECTED_UTIL));
		config.put(CFG_SELECTED_CRED, params.getString(CFG_SELECTED_CRED));
		config.put(CFG_APP_ID, params.getString(CFG_APP_ID));
		config.put(CFG_MAX_HIGH, params.getString(CFG_MAX_HIGH));
		config.put(CFG_MAX_MEDIUM, params.getString(CFG_MAX_MEDIUM));
		config.put(CFG_MAX_LOW, params.getString(CFG_MAX_LOW));
		return config;
	}
	
	@Override
	public Set<Requirement> calculateRequirements(TaskDefinition taskDefinition) {
		String selectedUtil = SA_CLIENT_UTIL_KEY + '.' + taskDefinition.getConfiguration().get(CFG_SELECTED_UTIL);
		Requirement req = new RequirementImpl(SYS_BUILDER_PREFIX + selectedUtil, true, ".*"); //$NON-NLS-1$
		return Collections.singleton(req);
	}
}