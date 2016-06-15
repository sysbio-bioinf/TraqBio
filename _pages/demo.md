---
layout: page
title: Demo
position: 1
---

This guide describes the setup for a quick demo installation of TraqBio.
The commandline code shown here indicates the entered commands by a `$` prefix.
Lines without the prefix are output from the previous entered command.

* TOC
{:toc}


## Setup

{:.information}
TraqBio has been tested with Java 7 and Java 8. \\
We recommended to use **Java 8**.

First create a directory `traqbio-test`:

``` shell
$ mkdir traqbio-test
$ cd traqbio-test
```

Download the [current release ({{ site.version }})](https://github.com/sysbio-bioinf/TraqBio/releases/download/v{{ site.version }}/traqbio-{{ site.version }}.jar)
of TraqBio and the exemplary [keystore.jks](https://github.com/sysbio-bioinf/TraqBio/raw/master/keystore.jks) into the created `traqbio-test` directory.
The files should be listed whe you run the following command:

``` shell
$ ls
keystore.jks  traqbio-{{ site.version }}.jar
```

To create an initial configuration file and an initial database run the following command:

``` shell
$ java -jar traqbio-{{ site.version }}.jar init
```

This command should not produce any output but creates the configuration file `traqbio.conf` and the database file `traqbio.db`.
You can verify this as follows:

``` shell
$ ls
keystore.jks  traqbio-{{ site.version }}.jar  traqbio.conf  traqbio.db
```

Finally, you can start TraqBio with:

``` shell
$ java -jar traqbio-{{ site.version }}.jar run
TraqBio started - Server listening on:
http://localhost:8000
https://localhost:8443
```

The output on the commandline indicates that TraqBio can be accessed from a web browser under `https://localhost:8443`.
Following the instructions for this demo, you have set up TraqBio with a self-signed certificate.
Hence, web browsers will warn you about that (see the following screenshots).
In Firefox and Chrome you can add a temporary exception for the certificate by clicking on **Advanced**.

Firefox:
![Warning: Self-signed Certificate Firefox](/images/Self-Signed-Firefox.png "Warning: Self-signed Certificate Firefox")

Chrome:
![Warning: Self-signed Certificate Chrome](/images/Self-Signed-Chrome.png "Warning: Self-signed Certificate Chrome")

After adding the temporary exception for the certificate, you will be presented with login page of TraqBio.
![TraqBio login page](/images/Login-Page.png "Login page")
The default credentials for the administrator account are the following:

| Username | Password |
|:--------:|:--------:|
| admin    | traqbio  |



## Enable E-mail Notification

E-mail notification is disabled by default since the technical details of the employed e-mail account need to be configured.
In the configuration file `traqbio.conf` there is a section containing the e-mail configuration which looks like:

``` clojure
:mail-config
 {:host-config
  {:host "mail.uni-ulm.de",
   :user "jsmith",
   :pass "secret",
   :tls :yes,
   :port 587},
  :from "john.smith@uni-ulm.de",
  ...
  :send-mail? false},
```

To enable the e-mail notification the key `:send-mail?` needs to be set to `true`.
Furthermore, you need to provide the address of your e-mail provider (`:host` and `:port`),
your e-mail user account (`:user`) and password (`:pass`).
The above example configures e-mail notification for TLS encrypted access to the mail server of Ulm University for a user "John Smith" with user account "jsmith". More details can be found in Section [E-mail Settings](/_pages/setup/#e-mail-settings) in the [Setup](/_pages/setup) documentation.

