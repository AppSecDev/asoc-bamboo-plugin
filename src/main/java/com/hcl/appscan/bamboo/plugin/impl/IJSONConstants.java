/**
 * (c) Copyright HCL Technologies Ltd. 2019.
 * LICENSE: Apache License, Version 2.0 https://www.apache.org/licenses/LICENSE-2.0
 */

package com.hcl.appscan.bamboo.plugin.impl;

import com.fasterxml.jackson.core.JsonPointer;

public interface IJSONConstants {
	
	JsonPointer STATUS	= JsonPointer.compile("/LatestExecution/Status");		//$NON-NLS-1$
	JsonPointer HIGH	= JsonPointer.compile("/LatestExecution/NHighIssues");		//$NON-NLS-1$
	JsonPointer MEDIUM	= JsonPointer.compile("/LatestExecution/NMediumIssues");	//$NON-NLS-1$
	JsonPointer LOW		= JsonPointer.compile("/LatestExecution/NLowIssues");		//$NON-NLS-1$
}
