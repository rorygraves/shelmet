scjhat
======

A standalone heap analysis tool, based on JHat (which is packaged with the JDK), rewritten using Scala and modern libraries.

Why?
----
As a professional developer JHat has saved my JHat has saved my life on many occasionally (esp at 3am).  But JHat has been shown
very little love since it was first built

Some issues:
+ Memory
+ Bugs
+ Awful display
+ Did we mention the memory and the bugs?

Immediate plans
--------

+ Memory Overflow to disk
+ Modern web engine (Spray/Spray http)
+ Improved layouts (Bootstrap + CSS)
+ Dynamic loading?
+ Retained size calculations
+ Better display of scala/java collections classes
+ A new query API based on scala repl
+ Proper tests

Installation
------------

SHelmet is packaged as a standalone jar including all of its dependencies so simply download it and use it as below.

TODO - Add link to package release area

Usage
-----

To analyse a heap simply

| java -jar shelmet-0.1.jar +heapfilename+

and then simply use a web browser to go to http://localhost:8080

If you run on a remote machine simply switch 'localhost' for the machine name or ip address.

You can control
and brows
TBC

Contributing
------------

This project is written in scala and built using sbt.

License
-------

As the Shelmet is based on the OpenJDK JHat is is considered a derivative work and is thus licensed under the
same GPL V2.0 (Please see LICENSE.txt)

By choice I would have used something a little more flexible.

Some included components are distributed under their own licenses:
+ [htmlshiv.js](https://code.google.com/p/html5shiv/) - licensed under MIT and GPL 2
+ [Twitter Bootstrap](https://github.com/twitter/bootstrap) - licensed under Apache License, Version 2.0
+ [jquery](http://jquery.com/) licensed under MIT License

Used Libraries
--------------
+ [SBT](sbt.io)
+ [Scala](http://scala.io)
+ [Spray](http://spray.io/)
+ [Scala Logging](https://github.com/typesafehub/scalalogging)
+ [LogBack](http://logback.qos.ch/)

