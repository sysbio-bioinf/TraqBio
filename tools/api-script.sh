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


