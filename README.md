# HCL AppScan on Cloud Bamboo Plug-in

Easily integrate [HCL AppScan on Cloud](https://cloud.appscan.com/) security scanning into your Atlassian Bamboo builds.

# Prerequisites

- An account on the [HCL AppScan on Cloud](https://cloud.appscan.com/) service. You'll also need to [create an application](https://help.hcltechsw.com/appscan/ASoC/ent_create_application.html) on the service and make note of its numeric ID in the browser URL. This ID will be required later when configuring the SAST scan task.
- The plug-in has been tested to run on Bamboo server version 5.13.2 or later.
- To build the plug-in, you will need to install the [Atlassian plug-in SDK](https://developer.atlassian.com/docs/getting-started).
- You will need to set up the Static Analyzer Client Utility on your Bamboo server (to initiate scans on local agents) or on remote agent machines. For information about obtaining and using the client utility, see [its docs](https://help.hcltechsw.com/appscan/ASoC/src_scanning.html).

# Building the Plug-in

- Navigate to the plug-in's root folder and issue the `atlas-package` command. The built plug-in JAR will be in the target folder.

# Installation and Configuration

1. Install the plug-in via the Bamboo administration dashboard. After installing the plug-in, it will appear in the list of user-installed add-ons.

   ![](https://github.com/AppSecDev/asoc-bamboo-plugin/blob/master/images/install1.png)

2. Use the Bamboo administration dashboard to add the SA Client capability to your server (for local agents) or to your remote agents. Specify the path to the Static Analyzer Client Utility.

   ![](https://github.com/AppSecDev/asoc-bamboo-plugin/blob/master/images/install2.png)

3. Enter your HCL AppScan on Cloud account [API Key ID and Secret Key](https://help.hcltechsw.com/appscan/ASoC/appseccloud_generate_api_key_cm.html) in the Bamboo shared credentials page.

# Adding the SAST Scan Task to your Build Plan

1. Add the SAST scan task to your build plan after your artifacts have been built. The SAST scan task will generate an intermediate representation of your artifacts and submit it to the cloud service for scanning.

   ![](https://github.com/AppSecDev/asoc-bamboo-plugin/blob/master/images/task1.png)

2. Enter information for the SAST scan task:

   ![](https://github.com/AppSecDev/asoc-bamboo-plugin/blob/master/images/task2.png)

   - Select the client utility to use.
   
   - Select the shared credentials to use when logging in to the cloud service.
   
   - Enter the ID of the application to associate with your scan.
   
   - Select whether the build job should wait for the scan to complete.
   
   - If you chose to wait for the scan to complete, then you can optionally specify the criteria by which a build failure occurs when security findings are found.

# Scan Results after a Build

1. The SAST scan task publishes the following artifacts:

   ![](https://github.com/AppSecDev/asoc-bamboo-plugin/blob/master/images/result1.png)

   - IRX - this is the intermediate representation of your artifacts that is uploaded to the cloud service for scanning.
   
   - Scan Results - HTML report of the security findings that are found (only if waiting for the scan to complete option is selected).

2. Messages about the outcome of the scan will also be written to the build log:

   ![](https://github.com/AppSecDev/asoc-bamboo-plugin/blob/master/images/result2.png)

# License

All files found in this project are licensed under [Apache License 2.0](LICENSE).
