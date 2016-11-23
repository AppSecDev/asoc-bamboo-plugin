/**
 * (c) Copyright IBM Corporation 2016.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.ibm.appscan.bamboo.plugin.impl;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.utils.i18n.I18nBean;

public class LogHelper {
	
	private I18nBean i18n;
	private BuildLogger logger;
		
	public LogHelper(I18nBean i18n) {
		this.i18n = i18n;
	}
	
	public void setLogger(BuildLogger logger) {
		this.logger = logger;
	}
	
	public String getText(String key, Object... args) {
		return i18n.getText(key, args);
	}
	
	public void info(String key, Object... args) {
		logger.addBuildLogEntry(getText(key, args));
	}
	
	public void error(String key, Object... args) {
		logger.addErrorLogEntry(getText(key, args));
	}
	
	public void debug(Object arg) {
		logger.addBuildLogEntry("##### " + arg + " #####"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}