
[@ui.bambooSection titleKey="section.util"]
	[@ww.label labelKey="label.util" name="selectedUtil"/]
[/@ui.bambooSection]

[@ui.bambooSection titleKey="section.asoc"]
	[@ww.label labelKey="label.cred" name="selectedCred"/]
	[@ww.label labelKey="label.appid" name="appId"/]
	[@ww.checkbox labelKey="label.suspend" name="suspendJob" toggle="true" disabled="true"/]
[/@ui.bambooSection]

[@ui.bambooSection titleKey="section.fail" dependsOn="suspendJob" showOn="true"]
	[@ww.label labelKey="label.high" name="maxHigh"/]
	[@ww.label labelKey="label.medium" name="maxMedium"/]
	[@ww.label labelKey="label.low" name="maxLow"/]
[/@ui.bambooSection]
