---
layout: page
title: Scripting API
position: 5
---

This page describes the Scripting API of TraqBio which can be used by external programs to interact via TraqBio.
This API is still **work in progress**.
Currently, the available operations allow the implementation of automated work steps in projects.

The Scripting API is implemented as a RESTful web service.
Hence, external programs are not required to run on the TraqBio server and can be implemented in any programming language.

The programs must use a valid TraqBio user account.
It is recommended to create a dedicated account so that changes of the program can be traced in the *timeline view* of TraqBio.

* TOC
{:toc}



## Operations

This section describes the available operations in general and provides usage examples for shell scripts.
In the shell script examples the program `curl` and the variable `$OPTS` will be used:

``` shell
# use -k only if a self-signed certificate is used
OPTS="-f -s -k -b cookies.txt -c cookies.txt"
```

The `cookies.txt` is used to keep session information between the single invocations of `curl`.
It is assumed that your TraqBio instance is accessible at `https://traqbio.your.domain.tld` ([TraqBio as Subdomain](/_pages/server#traqbio-as-subdomain)).
The [TraqBio as Subdirectory](/_pages/server#traqbio-as-subdirectory) and [TraqBio Standalone](/_pages/server#traqbio-standalone) setups work analogously.

### Login

First a program is required to post a login request:

> POST https://traqbio.your.domain.tld/login?username=**USER**&password=**PW**

Provided that the user accout name is stored in `$USER` and the password in `$PW` a login request via `curl` looks as follows:

``` shell
curl $OPTS "https://traqbio.your.domain.tld/login" -X POST --data-urlencode "username=$USER" --data-urlencode "password=$PW"
```

After a successful login the session will be stored in the `cookies.txt` as specified in `$OPTS` and used for every subsequent call.


### List Projects

The list of active projects can be read with the following request:

> GET https://traqbio.your.domain.tld/api/active-project-list

This returns a list of project ids separated with a *newline character* `\n`.

The `curl` invocation is the following:

``` shell
curl $OPTS "https://traqbio.your.domain.tld/api/active-project-list"
```

### Query Active Project Step

The query wether a given project step `STEPNAME` of a given project id `PID` is currently the active step that needs to be worked on is done via this request:

> GET https://traqbio.your.domain.tld/api/project/**PID**/is-active-step?name=**STEPNAME**

If the step is the active step of the project, then its id will be returned.
Otherwise, it will return an error.

With `curl` the invocation is the following:

``` shell
curl $OPTS "https://traqbio.your.domain.tld/api/project/$PID/is-active-step" -G --data-urlencode "name=$STEPNAME"
```
The error message in the case where step `$STEPNAME` is not the active step of project `$PID` is shown on the standard output when `-f` is removed from the options `$OPTS` passed to `curl`.

### Download Sample Sheet

The sample sheet of a project `PID` can be downloaded with the following request:

> GET https://traqbio.your.domain.tld/api/project/**PID**/sample-sheet

The sample sheet download with `curl` into a file `samplesheet.csv` is done as follows:

``` shell
curl $OPTS "https://traqbio.your.domain.tld/api/project/$PID/sample-sheet" -o samplesheet.csv
```

If there is no sample sheet in the given project, then no file will be downloaded.
The error message will be shown, when `-f` and `-o samplesheet.csv` are removed from the `curl` invocation.

### Finish Project Step

A project step `STEPID` of a project `PID` can be marked as finished via this request:

> POST "https://traqbio.your.domain.tld/api/project/**PID**/finish-step/**STEPID**

Furthermore, the attributes of that work step might be changed as well.

> POST "https://traqbio.your.domain.tld/api/project/**PID**/finish-step/**STEPID**?description=**DESCR**&freetext=**TEXT**&advisor=**ADVISOR**

To finish a step and modify its attributes, the following `curl` invocation can be used:

``` shell
curl $OPTS -X POST "https://traqbio.your.domain.tld/api/project/$PID/finish-step/$STEPID" --data-urlencode "description=$DESCR" --data-urlencode "freetext=$TEXT" --data-urlencode "advisor=$ADVISOR"
```

## Complete Example

The exemplary shell script [api-script.sh](https://raw.githubusercontent.com/sysbio-bioinf/TraqBio/master/tools/api-script.sh)
shows the general layout of an automated work step using the Scripting API:

``` shell
#!/bin/sh

SERVER="https://localhost:8443"
USERNAME="gv"
PASSWORD="gv"

# use -k only if a self-signed certificate is used
OPTS="-f -s -k -b cookies.txt -c cookies.txt"

STEPNAME="S4 D"
SAMPLESHEET="samplesheet.csv"

# login
curl $OPTS "$SERVER/login" -X POST --data-urlencode "username=$USERNAME" --data-urlencode "password=$PASSWORD"

for pid in $(curl $OPTS "$SERVER/api/active-project-list")
do
	echo "Active project:" $pid
	
	# detect whether the step with the specified name is the active step
	
	stepid=$(curl $OPTS "$SERVER/api/project/$pid/is-active-step" -G --data-urlencode "name=$STEPNAME")
	result=$?
	if [ $result -ne 0 ]
	then
		echo "Step \"$STEPNAME\" is not the active step of project $pid."
		continue
	fi
	
	echo "Active step \"$STEPNAME\" of project $pid has id $stepid."
	
	# get sample sheet
	
	curl $OPTS "$SERVER/api/project/$pid/sample-sheet" -f -o $SAMPLESHEET
	result=$?
	
	if [ $result -ne 0 ]
	then
		echo "Project $pid has no sample sheet."
		
		# we decide to continue if there is no sample sheet
		continue 
	fi
	
	echo "Sample sheet downloaded to $SAMPLESHEET."
	
	# do some analysis based on sample sheet
	
	# on success finish the active step
	
	curl $OPTS -X POST "$SERVER/api/project/$pid/finish-step/$stepid" --data-urlencode "description=Sophisticated analysis" --data-urlencode "freetext=Done. Results are available at ...." --data-urlencode "advisor=John Doe"
	result=$?
	
	if [ $result -ne 0 ]
	then
		echo "Marking step $stepid of project $pid as finished failed!"
	else
		echo "Step $stepid of project $pid has been marked as finished!"
	fi	
done
```

