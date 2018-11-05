# Compiling on MacOS

## Requirements
* [Xcode](https://itunes.apple.com/gb/app/xcode/id497799835)
* [JDK 10](http://www.oracle.com/technetwork/java/javase/downloads/jdk10-downloads-4416644.html)
* [Apache Ant](https://ant.apache.org/bindownload.cgi)
* [Apache Ivy](https://ant.apache.org/ivy/download.cgi)
* [NodeJS](https://nodejs.org/en/download/)
* [Node-AppDMG](https://github.com/LinusU/node-appdmg)

## Run following commands on Terminal

`ant resolve`
`ant jar`
`ant app`


Output files in /dist (.dmg file)



# Compiling on Windows

## Requirements
* [JDK 10](http://www.oracle.com/technetwork/java/javase/downloads/jdk10-downloads-4416644.html)
* [Apache Ant](https://ant.apache.org/bindownload.cgi)
* [Apache Ivy](https://ant.apache.org/ivy/download.cgi)
* [WiX Toolset](https://github.com/wixtoolset/wix3/releases/latest)

## Run following commands on CMD

`ant resolve`
`ant jar`
`ant msi`
`ant zip`

Output files in /dist 
