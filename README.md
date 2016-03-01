# TraqBio

TraqBio enables a simple and flexible tracking of core unit projects.
In addition classical laptop or desktop computers, it is also well usable from mobile devices like tablets and smartphones.

* Admin and user roles for your staff
* Customers will be notified by e-mail when you create a new or update (optionally) an existing project
* Customers can track the project's progress
* The creation of projects is accelerated by a simple templating system:
    - Use a template
    - Customize the provided project and project-steps
    - Create the new Project and save the customized template under a new name
* Ad hoc deviations from the predefined templates are possible.
* Keep track of your old projects with the archive page
* REST-based Scripting API (work in progress)
 
## Run in demo mode (with default config and without e-mail notification)

For a quick demo of TraqBio switch to the directory where you downloaded the [TraqBio jar file](https://github.com/sysbio-bioinf/TraqBio/releases/download/v1.3.5/traqbio-1.3.5.jar) and the exemplary [keystore.jks](keystore.jks?raw=true).

```
$ cd traqbio
$ ls
traqbio.jar keystore.jks
...
```
The run TraqBio as follows to create an initial configuration file and an initial database.
```
$ java -jar traqbio.jar init
```
The files ```traqbio.conf``` and ```traqbio.db``` should have been created.
```
$ ls
traqbio.jar keystore.jks
traqbio.conf traqbio.db
```
Finally, start the TraqBio web application.
```
$ java -jar traqbio.jar run
```

Now go to https://localhost:8443/ and sign in with the default credentials: username = *admin* and password = *traqbio*.

# Detailed description
## Prerequisites

When running TraqBio with SSL (:ssl? true), you need a Java KeyStore (*.jks) containing the SSL certificate to run TraqBio.
You can create a KeyStore with JDK's keytool.
http://docs.oracle.com/javase/7/docs/technotes/tools/windows/keytool.html

## Usage

To initialize TraqBio run the jar with the following parameters:

```
 java -jar traqbio.jar init
                              Default     Description  
                              -------     -----------  
  -a, --admin          NAME   admin       Name of the admin user  
  -p, --password       SECRET traqbio     Admins password
  -d, --data-base-name NAME   traqbio.db  Name of the database. TraqBio will not override a existing database file.
  -t, --template-file  NAME               Path to file with a initial set of templates.  
```

This will create the database with an administrator user and it will bootstrap a default config file.
Please update the entries in the config file (details below).

You can run TraqBio after the initialisation:

```  
 java -jar traqbio.jar run
                              Default       Description  
                              -------       -----------
  -c, --config-file FILENAME  traqbio.conf  Path to the config file
```
 
## Config

The following listing shows an example configuration for TraqBio.
(Actually this is the default configuration generated via ```java -jar traqbio.jar init```.)
```clojure
{; mostly Jetty config (see: https://ring-clojure.github.io/ring/ring.adapter.jetty.html )  
 :server-config
 {:keystore "keystore.jks",  
  :port 8000,
  :host "localhost",
  :join? false,
  :ssl? true,
  :key-password "password",
  :forwarded? false,
  :server-root "",         ; prefix directory for all TraqBio URLs (routes)
  :proxy-url nil,          ; hostname of the proxying server, e.g. via Apache (see below, Apache as Proxy)
  :ssl-port 8443},
  
 :data-base-name "traqbio.db", ; filename of SQLite database
 
 ; logging setup
 :log-level :info,
 :log-file "traqbio.log",
 
 :admin-shutdown? true, ; allow the TraqBio admin to shut down TraqBio from the web interface
 
 ; Branding
 :page-title "",      ; page title to display
 :page-title-link "", ; link to an external website to render beside the page title
 
 :upload-path "uploads/", ; Upload path for sample sheets and order forms. TraqBio needs write permission for
                          ; this folder. TraqBio will create subfolders for every project.
 
 ; e-mail configuration
 :mail-config
 {:send-mail? true ; Set this flag to false to deactivate the e-mail notification.
                   ; You can skip the remaining :mail-config in this case. 
  ; e-mail server configuration (see https://github.com/drewr/postal for detailed information)
  :host-config
  {:host "mail.uni-ulm.de",
   :user "jsmith", ; e-mail account username
   :pass "secret", ; e-mail account password
   :tls :yes,
   :port 587},  
  :from "john.smith@uni-ulm.de", ; visible sender e-mail address in TraqBio notification e-mails
  ; e-mail templates for project creation notification
  :project-creation
  {; template for notifying the staff
   :staff
   {:body project-creation-staff-message,
    :subject "Project {{projectnumber}} has been created"},
    ; template for notifying the customer
   :customer
   {:body project-creation-customer-message,
    :subject "{{projectnumber}} Sample Registration at the YOUR LAB"}},
  ; e-mail templates for project progress notification  
  :project-progress
  {; template for notifying the staff
   :staff
   {:body project-progress-staff-message,
    :subject "Progress update of project {{projectnumber}}"},
   ; template for notifying the customer
   :customer
   {:body project-progress-customer-message,
    :subject "Progress update of project {{projectnumber}}"}}}}
```

The templates of the message bodies are defined in before the configuration map as follows:
```clojure
(def project-creation-customer-message
"Dear {{customername}},

You recieve this e-mail because you have registered a project at the YOUR LAB.

You can track the progress of your samples through the various steps performed via the following link:
{{trackinglink}}

This e-mail was automatically generated by TraqBio, a sample tracking tool developed by the Research Group for Bioinformatics & Systems Biology, headed by Prof. Dr. Hans A. Kestler.

Yours sincerely,
             YOUR NAME
")
```

The settings for ```:subject``` and ```:body``` in the e-mail configuration are templates that determine the content of the notification e-mail.
The following place holders (whose name corresponds to the project attribute with the same name) can be used in the subject and the body templates. 
  * ```{{flowcellnr}}```
  * ```{{advisor}}```
  * ```{{customername}}``` or ```{{staffname}}```  (depending on the message)
  * ```{{customeremail}}``` or ```{{staffemail}}``` (depending on the message)
  * ```{{description}}```
  * ```{{dateofreceipt}}```
  * ```{{trackingnr}}```
  * ```{{trackinglink}}```
  * ```{{projectnumber}}```

## Initial template file
You can provide a file with a set of predefined project templates during the initialization.  
Use the [edn](https://github.com/edn-format/edn) syntax for the template file.

```clojure 
[                               ; A vector of templates   
    {:name              "text"  ; mandatory
     :description       "text"
     :templatesteps             ; A vector of template steps
     [
          {:type        "Step 1"  ; mandatory
           :description "a description of the first step"}
          {:type        "Step 2"  ; mandatory
           :description "a description of the second step"}          
     ]
    }
]
```

An example template file is the ```proteomics.init``` in this repository which can be used in the initilization as follows.
```
java -jar traqbio.jar init -t proteomics.init
```

## Scripting (work in progress)

TODO: more detailed description

Login in shell script:
```
curl -i -c cookies.txt -X POST -d 'username=john&password=secret' "https://your.traqbio.tld/login"
```
Perform requests in shell script:
```
curl -i -b cookies.txt -c cookies.txt "https://your.traqbio.tld/api/list-projects"
```
(Add "-k" when a self-signed certificate is used in a test/development scenario.)


```
curl -s -k -b cookies.txt -c cookies.txt "https://your.traqbio.tld/api/active-project-list"
```

```
for p in $(curl -s -k -b cookies.txt -c cookies.txt "https://your.traqbio.tld/api/active-project-list"); do echo "project" $p; done
```

```
curl -s -k -b cookies.txt -c cookies.txt "https://your.traqbio.tld/api/project/5/is-active-step" -G --data-urlencode "name=The step title"
```

```
curl -s -k -b cookies.txt -c cookies.txt -X POST "https://your.traqbio.tld/api/project/7/finish-step/25" --data-urlencode "description=la li lu" --data-urlencode "freetext=FERTIG!" --data-urlencode "advisor=Max Mustermann"
```

```
curl -s -k -b cookies.txt -c cookies.txt "https://your.traqbio.tld/api/project/7/sample-sheet" -f -o samplesheet.txt
```


## Linux Server

This section explains a fully featured setup on a Linux server with automatic startup of TraqBio when the server is rebooted.

### Init Script

TraqBio can be installed as a daemon on Linux via using the init script ```tools/traqbio``` (root privileges are required).
Modify the settings in the init script appropriately and copy it to ```/etc/init.d/```.
The settings section of the init script looks as follows:

```
## TraqBio settings

VERSION="1.0.0"
INSTALL_DIR="/home/youruser/traqbio"
CONFIG="traqbio.conf"
OPTS=""

## Linux user and group for TraqBio process
RUN_AS_USER="traqbio"
RUN_AS_GROUP="traqbio"

## memory settings for the JVM
MIN_MEMORY="256M"
MAX_MEMORY="512M"
```

Set the install directory ```INSTALL_DIR``` to the one where you copied the TraqBio jar file and initialized the database.
If you choose a different configuration name, you will have to adjust the variable ```CONFIG``` appropriately.
It is recommended to create a user account dedicated to run TraqBio.
In the example above a user account ```traqbio``` with group ```traqbio``` is used.
The minimal and maximal memory that is used by the JVM can be adjusted to your usage scenario.

After copying ```tools/traqbio``` to ```/etc/init.d/traqbio``` you can configure the automatic startup on server boot
via the operating system tools, e.g.  ```rcconf```.
Manual start, stop and restart is possible via the following:

```
/etc/init.d/traqbio start
/etc/init.d/traqbio stop
/etc/init.d/traqbio restart
```

### Apache as Proxy

We describe two possibilities to use Apache as a proxy for BioTrac:

1. Using a subdomain of your domain (virtual host).
   You need to have a virtual host definition in ```/etc/apache2/sites-available/traqbio``` like the following:
```
ProxyRequests Off
ProxyPreserveHost On

<VirtualHost *:80>
  ServerName traqbio.yourhost.de
  Redirect permanent / https://traqbio.yourhost.de/
</VirtualHost>


<VirtualHost *:443>
  ServerName traqbio.yourhost.de

  SSLEngine On
  SSLProxyEngine On
  SSLProxyVerify none
  SSLCertificateFile /path/to/certificate.crt
  SSLCertificateKeyFile /path/to/keyfile.key

  ProxyPass / http://localhost:TRAQBIO-PORT/
  ProxyPassReverse / http://localhost:TRAQBIO-PORT/
  RequestHeader set X-Forwarded-Proto "https"
  ErrorLog /var/log/apache2/traqbio.log
  CustomLog /var/log/apache2/traqbio.log common
</VirtualHost>
```
This sets up a a virtual host ```traqbio.yourhost.de``` on your domain ```yourhost.de``` using SSL encryption.
You need to specify the option ```:proxy-url "traqbio.yourhost.de``` in the TraqBio server configuration (```:server-config```).
You have to fill in ```TRAQBIO-PORT``` with the port you specified in your TraqBio configuration file.
The paths to the certificate file ```/path/to/certificate.crt``` and the certificate key file ```/path/to/keyfile.key``` must be filled in according to your server configuration.
    
2. Using a subdirectory. You need to add the following to your existing virtual host:
```
<Location /traqbio>
  ProxyPass        http://localhost:TRAQBIO-PORT/traqbio
  ProxyPassReverse http://localhost:TRAQBIO-PORT/traqbio
</Location>
```
You have to fill in ```TRAQBIO-PORT``` with the port you specified in your TraqBio configuration file.
You also need to specify ```ProxyRequests Off``` and ```ProxyPreserveHost On``` before the existing virtual host, e.g.
```
ProxyRequests Off
ProxyPreserveHost On

<VirtualHost *:443>
  ServerName yourhost.de
  
  ...
  
  <Location /traqbio>
    ProxyPass        http://localhost:TRAQBIO-PORT/traqbio
    ProxyPassReverse http://localhost:TRAQBIO-PORT/traqbio
  </Location>
</VirtualHost>
```
In this case you also have to specify the options ```:server-root "traqbio"``` and ```:proxy-url "yourhost.de``` in the TraqBio server configuration (```:server-config```).

## Database

The database that TraqBio uses is a SQLite database.
The [SQLite Database Browser](http://sqlitebrowser.org/) can be used to inspect the data stored in the database.

## License

TraqBio is distributed under the MIT License:

The MIT License (MIT)

Copyright Fabian Schneider and Gunnar Völkel © 2014-2016

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
