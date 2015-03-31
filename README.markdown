lzma-testing
============

You need:

 * Java 7 (apt-get install openjdk-7-jdk)
 * Maven (apt-get install maven)

Build with:

    $ mvn package

Compression testing:

    $ ./compress lzma input.txt 8

Where the compression scheme is *lzma*, the input is *input.txt*, and the
multiplier is *8*.  The scheme must be one of *lzma* or *gzip*.

Decompression testing:

    $ ./decompress lzma input.txt 8

Where the compression scheme is *lzma*, the base input file (the one supplied
during compression) is *input.txt*, and the multiplier is *8*.  Decompression
arguments should match those used during compression; The decompression tester
expects to find the files created during compression testing.