---
layout: page
title: Linux Server
position: 4
---

This section explains a fully featured setup on a Linux server with automatic startup of TraqBio when the server is rebooted.
There are two setup scenarios for TraqBio:

1. TraqBio standalone
2. TraqBio behind proxy

A similar setup is possible on windows servers as well, though the means to achieve it are different.



* TOC
{:toc}


## Automatic Startup

TraqBio can be installed as a daemon on Linux via using the init script [traqbio](https://raw.githubusercontent.com/sysbio-bioinf/TraqBio/master/tools/traqbio) (root privileges on the server are required).
Modify the settings in the init script appropriately and copy it to `/etc/init.d/`.
The settings section of the init script looks as follows:

```
## TraqBio settings

VERSION="1.3.6"
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

Set the install directory `INSTALL_DIR` to the one where you copied the TraqBio jar file and initialized the database.
If you choose a different configuration name, you will have to adjust the variable `CONFIG` appropriately.
It is recommended to create a user account dedicated to run TraqBio.
In the example above a user account `traqbio` with group `traqbio` is used.
The minimal and maximal memory that is used by the JVM can be adjusted to your usage scenario.

After copying the init script `traqbio` to `/etc/init.d/traqbio` you can configure the automatic startup on server boot
via the operating system tools, e.g.  `rcconf`.
Manual start, stop and restart is possible via the following:

```
/etc/init.d/traqbio start
/etc/init.d/traqbio stop
/etc/init.d/traqbio restart
```


## TraqBio Standalone

For the standalone scenario only the init script needs to be setup as described in the Section [Automatic Startup](automatic-startup).
The `:server-config` in the configuration file should set the port to `80`, the ssl port to `442` and the domain name of your server as host.

``` clojure
{:server-config
 {:host "your.domain.tld",
  :port 80,
  :ssl? true,
  :ssl-port 443,
  ... },
 ... }
```


## TraqBio behind Proxy

This section describes the TraqBio setup using [Apache](https://httpd.apache.org/) as proxy.
In the proxy scenario the transport encryption is provided by Apache such that transport encryption is disabled in TraqBio.
Therefore, the init script setup from Section [Automatic Startup](automatic-startup) should be performed in advance.

We describe two possibilities to use Apache as a proxy for TraqBio:

1. TraqBio as a subdomain of your domain (virtual host).
2. TraqBio as a subdirectory.


### TraqBio as Subdomain

To setup TraqBio to use a subdomain of your domain (virtual host),
you need to have a virtual host definition in `/etc/apache2/sites-available/traqbio` like the following:

``` apache
ProxyRequests Off
ProxyPreserveHost On

<VirtualHost *:80>
  ServerName traqbio.your.domain.tld
  Redirect permanent / https://traqbio.your.domain.tld/
</VirtualHost>


<VirtualHost *:443>
  ServerName traqbio.your.domain.tld

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

This sets up a a virtual host `traqbio.your.domain.tld` on your domain `your.domain.tld` using transport encryption.
The virtual host is forwarded to the chosen port `TRAQBIO-PORT` on `localhost` which TraqBio listens to.
The paths to the certificate file `/path/to/certificate.crt` and the certificate key file `/path/to/keyfile.key` 
must be filled in according to your server configuration.
The `:server-config` of the TraqBio configuration has to look like:

``` clojure
{:server-config
 {:host "localhost",
  :port TRAQBIO-PORT,
  :server-root "",

  :proxy-url "traqbio.your.domain.tld",
  :forwarded? true,

  :ssl? false},
 ... }
```

### TraqBio as Subdirectory

To setup TraqBio to use a subdirectory of your domain, you need to add the following to your existing virtual host:

``` apache
<Location /traqbio>
  ProxyPass        http://localhost:TRAQBIO-PORT/traqbio
  ProxyPassReverse http://localhost:TRAQBIO-PORT/traqbio
</Location>
```

You have to fill in `TRAQBIO-PORT` with the port you specified in your TraqBio configuration file.
You also need to specify `ProxyRequests Off` and `ProxyPreserveHost On` before the existing virtual host, e.g.

``` apache
ProxyRequests Off
ProxyPreserveHost On

<VirtualHost *:443>
  ServerName your.domain.tld
  
  ...
  
  <Location /traqbio>
    ProxyPass        http://localhost:TRAQBIO-PORT/traqbio
    ProxyPassReverse http://localhost:TRAQBIO-PORT/traqbio
  </Location>
</VirtualHost>
```

This sets up a directory `traqbio` on your domain `your.domain.tld` such that TraqBio is accessible via `your.domain.tld/traqbio`.
The directory `traqbio` of the virtual host is forwarded to the chosen port `TRAQBIO-PORT` on `localhost` which TraqBio listens to.
The `:server-config` of the TraqBio configuration has to look like:

``` clojure
{:server-config
 {:host "localhost",
  :port TRAQBIO-PORT,
  :server-root "traqbio",

  :proxy-url "your.domain.tld",
  :forwarded? true,

  :ssl? false},
 ... }
```
