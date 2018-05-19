## OCE Graph Generator
OCE network graph generator project using igraph

### Installation

#### Requirements

Make sure you have the following before installing igraph:

- C and C++ compilers such as gcc.
- The GNU make tool.
- Optionally the libxml2 library for reading GraphML files.

#### Compilation
The standard installation method uses the autoconf/automake toolset. Run the following commands from the top-level directory of the code.

```
./configure
 make
 make check
 make install
 ```
 
 #### Running OCE Core
 
 OCE Core is Java client that connects to C module (oce_core.c) using JNI
 For MacOS it can be run:
 ```
$ gcc -shared -fPIC -I$(/usr/libexec/java_home -v 1.8)/include -I$(/usr/libexec/java_home -v 1.8)/include/darwin oce_core.c -o libOCECore.jnilib
$ javac OCECore.java
$ javah -classpath . OCECore
$ java -classpath . -Djava.library.path=. OCECore
```
where 
```
gcc -shared -fPIC ...
```
compiles and creates library for oce_core.c
```
javac OCECore.java
```
compiles Java client
```
javah -classpath . OCECore
```
generates header file

```
java -classpath . -Djava.library.path=. OCECore
```
runs OCECore java class (with library path specified to find generated c module library)

 #### Running OCE Graph Gen
To run sample example 'oce_graph_gen':
```
gcc oce_graph_gen.c -Iigraph/include -L/usr/local/lib -ligraph -o oce_graph_gen
```
Providing that igraph libraries are installed in /usr/local/lib

*Note: For MacOS You can use the regular Unix way, or Homebrew, the homebrew/science/igraph formula.
Example:
```
brew install igraph
```

For more information about igraph library go to:

http://igraph.org/c/doc/index.html

## Basics

[Data structure library: vector, matrix, other data types](http://igraph.org/c/doc/ch07.html)
