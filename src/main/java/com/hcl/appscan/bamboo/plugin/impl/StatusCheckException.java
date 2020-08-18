/**
 * (c) Copyright IBM Corporation 2016.
 * (c) Copyright HCL Technologies Ltd. 2020.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.hcl.appscan.bamboo.plugin.impl;

import com.atlassian.bamboo.task.TaskException;

public class StatusCheckException extends TaskException {
	
	private static final long serialVersionUID = 1900809086587356445L;
	
	public StatusCheckException(String message) {
		super(message);
	}
}