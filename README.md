# wordle-solver
A Wordle solver just for fun, written in Java. It uses a simple command-line interface. It can either be used in interactive, simulator or benchmark mode.
It does not use any advanced statistics or a calculated decision tree, but simply uses the information gathered from each guess to exclude words that directly contradict that information, much like most humans (probably?) do.

The statistics gathered from the benchmark notes an average guess rate of 3.80 to solve a Wordle, but it does fail on 23 out of the 2315 words, where the correct word is not found within the six guesses.

## Interactive Mode
In this case the program produces a guess, which then manually has to be entered into the Wordle in question, and then Wordle's reply needs to be fed back to the program so that it can use that information to make a second guess. The syntax for this is to write (separated by spaces)
* The actual character for a correct guess (green)
* A hyphen '-' for a grey guess
* The character immediately followed by a hyphen for a yellow guess

Example: Let's say we guessed "salet", but the word is "abbey". The corresponding string to input into the program is then (excluding quotation marks):

`"- a- - e -"`

i.e. the letters s, l and t are not present, a exists but is in the wrong position and e exists and is in the right position.

## Simulator Mode
In this mode, you simply provide the correct word to the program, and it will try to guess it

## Benchmark Mode
This mode simply performs one simulation run of each word within the dictionary and reports aggregated stats over all runs.

# Prerequisites
* Java 14
* Maven

# How to Build and Run
To build this project simply run: `mvn clean package` in the project directory. This command will generate a file called target/wordle-solver-0.0.1-SNAPSHOT.jar.

This file can then be executed by typing `java -jar wordle-solver-0.0.1-SNAPSHOT.jar`

# How to Build and Run in Docker
This project can be packaged and run as a Docker container.

First build the container by running `docker build -t wordle-solver .`

Then run the container with `docker run -i wordle-solver`

