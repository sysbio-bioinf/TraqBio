---
layout: page
title: Setup
position: 3
---

This page contains the information needed for permanent setup of TraqBio.

* TOC
{:toc}

(In case you only want to try out TraqBio quickly see [Demo](/_pages/demo).)


## Prerequisites

{:.information}
TraqBio has been tested with Java 7 and Java 8. \\
We recommended to use **Java 8**.

TraqBio should be run with Transport Layer Security (TLS).
Hence, a digital certificate for the intended domain of your TraqBio server is required.
If you run TraqBio on a server that is already used as web server with *https*,
you'll be able to use its certificate ([jks import](https://docs.oracle.com/javase/tutorial/security/toolsign/rstep2.html)).
Otherwise you either need to get a certificate (from a commercial vendor or [Let's Encrypt](https://letsencrypt.org/))
or create a certificate and sign it yourself.
The latter is done for the [Demo](/_pages/demo) setup.
The keystore.jks in the project repository has been generated as follows:

``` shell
$ keytool -genkey -keyalg RSA -alias selfsigned -keystore keystore.jks -storepass password -validity 365 -keysize 2048
```

A self-signed certificate has the disadvantage that the users are warned by their web browsers that their connection is not secure.
Hence, this should only be used for test setups.


## Initialisation and Startup

TraqBio is intialized via `java -jar traqbio-{{ site.version }}.jar init` which allows the following parameters:

``` shell
$ java -jar traqbio-{{ site.version }}.jar init -h
Initialise the TraqBio instance.

  -a, --admin NAME           admin       Name of the admin user
  -p, --password SECRET      traqbio     Admins password
  -d, --data-base-name NAME  traqbio.db  Name of the database. TraqBio will not override a existing database file.
  -t, --template-file NAME               Path to file with a initial set of templates.
  -h, --help
```

`--admin`
 : login name of the admin account

`--password`
 : password of the admin account

`--data-base-name`
 : filename of the TraqBio database to create (existing databases are not overriden)

`--template-file`
 : filename of a file containing template definitions which shall be imported into the initial database

The init call will create the TraqBio database with the default or specified administrator user and a default configuration file.
The configuration file should be adjusted to the concrete usage scenario.

After the intialisation TraqBio is started as follows:

``` shell
$ java -jar target/traqbio-{{ site.version }}.jar run
TraqBio started - Server listening on:
http://localhost:8000
https://localhost:8443
```

The default configuration will start TraqBio only on the localhost -- to be able to access the server remotely the server settings need to be adjusted.

To use different configuration files, you can specify the configuration to use as follows:

``` shell
$ java -jar target/traqbio-{{ site.version }}.jar run -c another-traqbio.conf
```

## Configuration

The TraqBio configuration file allows customisation for different setup scenarios.
The configuration file contains a Clojure map to specify the various settings.
Consider the following short excerpt from the default configuration:

``` clojure
{:server-config
 {:host "localhost",
  :port 8000},

 :data-base-name "traqbio.db"
 ... }
```


Each map is started by `{` and ended by `}`.
Settings are specified by keywords such as `:port` followed by a value such as `8000`.
Keywords also identify configuration for subcomponents of TraqBio, e.g. the server configuration `:server-config`.


### General Settings

TraqBio offers the following general settings for customization:

``` clojure
{ ...
 :data-base-name "traqbio.db"
 :upload-path "uploads/",

 :log-level :info,
 :log-file "traqbio.log",

 :admin-shutdown? true,

 :page-title "Your Core Unit",
 :page-title-link "http://your.core.unit.com",
 
 ... }
```

The effect of these settings is described in the following.

`:data-base-name`
 : path to the database that TraqBio uses to store all account and project data.

`:upload-path`
 : path to the root directory where the project attachments "order form" and "sample sheet" are stored.

`:page-title`
 : specifies the shown title of TraqBio. You can use the name of your core unit here.

`:page-title-link`
 : specifies a link that is shown besides the page title. You can specify the link to the web page of your core unit here.

`:log-level`
 : specifies how much information is written into the log file. Possible values: `:trace`, `:debug`, `:info`, `:warn`, `:error`, `:fatal`

`:log-file`
 : path to the the log file.

`:admin-shutdown?`
 : enables or disables the shutdown button for the administrator account in the web interface.



### Server Settings

TraqBio ships with a built-in web server ([Jetty](http://www.eclipse.org/jetty/)).
There are two setup scenarios for TraqBio:

1. TraqBio standalone
2. TraqBio behind proxy

This section explains the server related settings in general.
The two setups for a linux server are described in [Linux server](/_pages/server).

The following listing shows the relevant server settings.
Other settings together with a short description can be found in the [ring library documentation](https://ring-clojure.github.io/ring/ring.adapter.jetty.html).

``` clojure
{:server-config
 {:host "localhost",
  :port 8000,
  :server-root "",

  :proxy-url nil,
  :forwarded? false,

  :ssl? true,
  :ssl-port 8443,
  :keystore "keystore.jks",
  :key-password "password"},
 ... }
```

The server settings are located in the `:server-config`.
The following properties are used:

`:host`
 : specifies the network interface to which the web server binds.
   The host is also used for the generation of links to TraqBio pages.
   Use `"localhost"` for access from the local computer.
   Use the domain name or IP of the network interface that connects to the internet for remote access.

`:port`
 : specifies the port on which the web server will listen.

`:server-root`
 : specfies the prefix path under which the TraqBio server pages are available, e.g. `"traqbio"` when TraqBio is run at `your.domain.tld/traqbio`

`:proxy-url`
 : When TraqBio is used behind a proxy, you need to specify the URL under which TraqBio is accessible at the proxy.

`:forwarded?`
 : When TraqBio is used behind a proxy, you need to set this to `true`. Otherwise, keep `false`.

`:ssl?`
 : enables or disables transport encryption (aka "https").

`:ssl-port`
 : specifies the port used for encrypted communication.

`:keystore`
 : specified the filename of the keystore that contains the certificate for the transport encryption.

`:key-password`
 : specifies the password needed to access the keystore (if any).


### E-mail Settings

E-mail notification is enabled by specifying `:send-mail? true` in the `:mail-config` map.
The displayed sender of the e-mail is specified via the `:from` setting.
The following example configures TLS encrypted access to the mail server of Ulm University for a user "John Smith" with user account "jsmith".

``` clojure
{ ...
 :mail-config
 {:send-mail? true,
  :from "john.smith@uni-ulm.de",
  :host-config
  {
   :host "mail.uni-ulm.de",
   :port 587,
   :tls :yes,
   :user "jsmith",
   :pass "secret"},
  ... }
 ... }
```

The e-mail server configuration is located in the `:host-config`.
The following properties are needed for an encrypted connection to the e-mail server:

`:host`
 : address (URL or IP) of the e-mail server

`:port`
 : port of the e-mail server. Encrypted and plaintext connection setups usually have different ports. Consult your e-mail provider for details.

`:tls`
 : enable or disable TLS encryption (`:yes` or `:no')

`:user`
 : user account at the e-mail server

`:pass`
 : password of the e-mail user account


### Notification Settings

TraqBio sends notification e-mails to the customers on project creation.
When enabled in the configuration, customers also receive notification e-mails after the completion of work steps in their projects.
Core unit staff can be added as CC to those e-mails.
In addition to that individual staff members can be set up to be notified in separate e-mails on work step completion.

#### General Notification Settings

{% raw %}
``` clojure
{ ...
 :mail-config
 {:send-mail? true,
  :host-config { ... },
  :cc-notified-staff? true,
  :project-creation
  {:staff
   {:body project-creation-staff-message,
    :subject "Project {{projectnumber}} has been created"},
   :customer
   {:body project-creation-customer-message,
    :subject "{{projectnumber}} Sample Registration at the YOUR LAB"}},
  :project-progress
  {:staff
   {:body project-progress-staff-message,
    :subject "Progress update of project {{projectnumber}}"},   
   :customer
   {:body project-progress-customer-message,
    :subject "Progress update of project {{projectnumber}}"}}}
 ... }
```
{% endraw %}

The notification messages for the two events `:project-creation` and `:project-progress` can be adjusted to your needs by specifying
templates for the `:subject` and the `:body` of the e-mail.
You can specify different templates for `:staff` and `:customers` since you might choose to provide additional information
for your staff, e.g. a direct link to be able to edit the project.


#### Templates and Placeholders

The `:body` templates are stored in separate variables such as `project-creation-staff-message` for better readability of the configuration.
Such a template is defined before the configuration map as in the following example.

{% raw %}
``` clojure
(def project-progress-staff-message
"Dear {{staffname}},

project {{projectnumber}} for customer {% if customername %}{{customername}} ({{customeremail}}){% else %}{{customeremail}}{% endif %} has been updated.

{{progressinfo}}

You can access the project for modifications via the following link:
{{editlink}}

The tracking view is available via the following link:
{{trackinglink}}

Yours sincerely,
             YOUR NAME
")
```
{% endraw %}

As shown in the example above you can even use simple conditions in the template.
In this example the customer name is only inserted in the message, provided that it is known.

{% raw %}
``` clojure
"{% if customername %}{{customername}} ({{customeremail}}){% else %}{{customeremail}}{% endif %}"
```
{% endraw %}


Both, `:subject` and `:body` templates, can use the following placeholders which are filled in by TraqBio:

{% raw %}
`{{projectnumber}}`
 : the project number which identifies the project

`{{description}}`
 : the description text of the project

`{{advisor}}`
 : the advisor of the project

`{{trackingnr}}`
 : the tracking number of the project

`{{trackinglink}}`
 : the tracking link of the project (must be included in the project creation message)

`{{editlink}}`
 : the link to the edit page of the project for staff notifications

`{{dateofreceipt}}`
 : the date of the project creation

`{{progressinfo}}`
 : detail information of the project progress (only in progress notification)

`{{customername}}` or `{{staffname}}`
 : the name of the addressed customer or staff member (depending on the message)

`{{customeremail}}` or `{{staffemail}}`
 : the e-mail address of the customer or staff member (depending on the message)

`{{flowcellnr}}`
 : the flow cell number associated with the project (if any) 
    
{% endraw %}


### Default Configuration

The following listing shows the default configuration of TraqBio as generated via the initialisation.

{% raw %}
``` clojure
; message template for progress notification to staff
(def project-progress-staff-message
"Dear {{staffname}},

project {{projectnumber}} for customer {% if customername %}{{customername}} ({{customeremail}}){% else %}{{customeremail}}{% endif %} has been updated.

{{progressinfo}}

You can access the project for modifications via the following link:
{{editlink}}

The tracking view is available via the following link:
{{trackinglink}}

Yours sincerely,
             YOUR NAME
")

; message template for progress notification to customer
(def project-progress-customer-message
"Dear {{customername}},

project {{projectnumber}} has been updated.

{{progressinfo}}

You can access additional information using the following link:
{{trackinglink}}

Yours sincerely,
             YOUR NAME
")

; message template for project creation notification to staff
(def project-creation-staff-message
"Dear {{staffname}},

Project {{projectnumber}} has been created for customer {% if customername %}{{customername}} ({{customeremail}}){% else %}{{customeremail}}{% endif %}.
You have been registered for notification e-mails about the progess of this project.

You can access the project for modifications via the following link:
{{editlink}}

The tracking view is available via the following link:
{{trackinglink}}

Yours sincerely,
             YOUR NAME
")

; message template for project creation notification to customer
(def project-creation-customer-message
"Dear {{customername}},

You recieve this e-mail because you have registered a project at the YOUR LAB.

You can track the progress of your samples through the various steps performed via the following link:
{{trackinglink}}

This e-mail was automatically generated by TraqBio, a sample tracking tool developed by the Research Group for Bioinformatics & Systems Biology, headed by Prof. Dr. Hans A. Kestler.

Yours sincerely,
             YOUR NAME
")

{; mostly Jetty config (see: https://ring-clojure.github.io/ring/ring.adapter.jetty.html ) 
 :server-config
 {; prefix directory for all TraqBio URLs (routes)
  :server-root "",
  ; hostname of the proxying server, e.g. via Apache
  :proxy-url nil,
  ; set to true, if a proxy is used
  :forwarded? false,
  ; TraqBio host (for remote access, e.g. biotraq.your.domain.tld)
  :host "localhost",
  ; TraqBio port
  :port 8000,
  ; enable/disable https
  :ssl? true,
  ; TraqBio ssl port for https
  :ssl-port 8443,
  ; keystore file and password
  :keystore "keystore.jks",
  :key-password "password"},

 ; Upload path for sample sheets and order forms. TraqBio needs write permission for
 ; this folder. TraqBio will create subfolders for every project.
 :upload-path "uploads/",
 ; filename of SQLite database
 :data-base-name "traqbio.db"

 ; logging setup
 :log-level :info,
 :log-file "traqbio.log",
 ; allow the TraqBio admin to shut down TraqBio from the web interface
 :admin-shutdown? true,

 ; Branding
 ; page title to display
 :page-title "Your Core Unit",
 ; link to an external website to render beside the page title
 :page-title-link "http://your.core.unit.com",

 ; e-mail configuration (see https://github.com/drewr/postal for detailed information)
 :mail-config
 {:host-config
  {; mail server URL of e-mail provider
   :host "mail.uni-ulm.de",
   ; port at mail server (TLS port in this case)
   :port 587,
   ; enable/disable encryption (encryption strongly recommended)
   :tls :yes,
   ; e-mail account username
   :user "jsmith",
   ; e-mail account password
   :pass "secret"},
  ; visible sender e-mail address in TraqBio notification e-mails
  :from "john.smith@uni-ulm.de",
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
    :subject "Progress update of project {{projectnumber}}"}},
  ; enable/disable email CC to notified staff for customer notifications
  :cc-notified-staff? false,
  ; Set this flag to false to deactivate the e-mail notification.
  ; You can skip the remaining :mail-config in this case.
  :send-mail? false}}
```
{% endraw %}
