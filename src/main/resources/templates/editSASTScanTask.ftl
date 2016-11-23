
[@ui.bambooSection titleKey="section.util"]
	[@ww.select labelKey="label.util" name="selectedUtil" required="true" list="utilList"/]
[/@ui.bambooSection]

[@ui.bambooSection titleKey="section.asoc"]
	[@ww.select labelKey="label.cred" name="selectedCred" required="true" list="credList"/]
	[@ww.textfield labelKey="label.appid" name="appId" required="true"/]
[/@ui.bambooSection]

[@ui.bambooSection titleKey="section.fail" descriptionKey="section.fail.desc"]
	[@ww.textfield labelKey="label.high" name="maxHigh"/]
	[@ww.textfield labelKey="label.medium" name="maxMedium"/]
	[@ww.textfield labelKey="label.low" name="maxLow"/]
[/@ui.bambooSection]
