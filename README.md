# ModelPolisher 
**Annotating Systems Biology Models**

<img align="right" src="doc/img/ModelPolisherIcon256.png" width="64"/>

*Authors:* [Andreas Dräger](https://github.com/draeger/), [Thomas J. Zajac](https://github.com/mephenor/), [Matthias König](https://github.com/matthiaskoenig), [Kaustubh Trivedi](https://github.com/codekaust)

[![Build Status](https://travis-ci.org/draeger-lab/ModelPolisher.svg?branch=master?style=plastic)](https://travis-ci.org/draeger-lab/ModelPolisher)
[![Stable version](https://img.shields.io/badge/Stable_version-2.0-brightgreen.svg?style=plastic)](https://github.com/draeger-lab/ModelPolisher/releases/)
[![DOI](http://img.shields.io/badge/DOI-10.1371%20%2F%20journal.pone.0149263-blue.svg?style=plastic)](https://doi.org/10.1371/journal.pone.0149263)
[![License (MIT)](https://img.shields.io/badge/license-MIT-blue.svg?style=plastic)](http://opensource.org/licenses/MIT)

ModelPolisher accesses the [BiGG Models knowledgebase](http://bigg.ucsd.edu) to annotate and autocomplete [SBML](http://sbml.org) models.
Thereby, the program mainly relies on [BiGG identifiers](https://github.com/SBRG/bigg_models/wiki/BiGG-Models-ID-Specification-and-Guidelines) for model components.
Moreover, it fixes some apparent errors in the models and uses mappings from [AnnotateDB](https://github.com/matthiaskoenig/annotatedb) to further add annotations.

ModelPolisher is primarily a command-line based tool. You can run ModelPolisher locally [using `Docker`](#using-docker) or using  your installation of BiGG Models database (see https://github.com/SBRG/bigg_models). 

Note: If you run into trouble with larger files, especially `_java.lang.OutOfMemoryError: GC overhead limit exceeded_`, please use the JVM `Xmx` flag (e.g., `java -Xmx4G -jar ModelPolisher-VERSION.jar` ).

# Table of Contents

* [How to cite ModelPolisher?](#cite-ModelPolisher)
* [How to build?](#build-instructions)
* [How to run ModelPolisher?](#run-ModelPolisher)
  * [Using Docker](#using-docker)
  * [Using ModelPolisher jar](#using-jar)
* [Licenses](#licenses)

# <a name="cite-ModelPolisher"></a>How to cite ModelPolisher?

The online version of ModelPolisher is described in this article: http://journals.plos.org/plosone/article?id=10.1371/journal.pone.0149263

The article ["BiGG Models: A platform for integrating, standardizing and sharing genome-scale models"](https://nar.oxfordjournals.org/content/44/D1/D515) describes BiGG Models knowledge-base including ModelPolisher.

# <a name="build-instructions"></a>How to build?

NOTE: You may run ModelPolisher, without building, using Docker (preferred). See [here](#using-docker).

ModelPolisher uses `gradle` to build. Make sure you have `gradle (version >= 5.0)` installed in your system before following the procedure below.

First clone this github project and go to directory `<path>/ModelPolisher/`. Then, ModelPolisher can be built using Gradle. Gradle runs task `lightJar` by default which produces `jar` file, with dependencies, for ModelPolisher in `<path>/ModelPolisher/target/` directory. So, in order to build ModelPolisher run any of following command under `ModelPolisher/` directory:

`gradle lightJar` or `gradle`  
Additionally `gradle devel` is provided to easily run a non-release version with [Docker](#non-release).

NOTE: `lightJar` requires `postgresql` database(s) to be set-up to run ModelPolisher.

# <a name="run-ModelPolisher"></a>How to run ModelPolisher? : Polish Models
We recommend users to run ModelPolisher [using `docker`](#using-docker), though ModelPolisher can be run [from ModelPolisher `jar`](#using-jar), which is preferred for developers.

NOTE: ModelPolisher provides various command line options which can be seen using:
```
java -jar <path>/ModelPolisher/target/ModelPolisher-2.1.jar -? 
```

In the example commands below few options are taken by default. See list of mostly used options [here](https://github.com/draeger-lab/ModelPolisher/wiki/Mostly-Used-Command-Line-Options).

## <a name="using-docker"></a>Using Docker
ModelPolisher can be run in docker containers. This allows user to skip the build process, and database setup required to run ModelPolisher. Please install `docker` and `docker-compose`, if not installed already.

Clone ModelPolisher and change directory to ModelPolisher/:
```
git clone https://github.com/draeger-lab/ModelPolisher/
cd <path>/ModelPolisher
```
Now, bring up docker containers required for ModelPolisher(NOTE: You must be in `ModelPolisher/` directory):
```
docker-compose up
```

We recommend using docker-compose in non-detached mode when building containers for first time. If you have previously built ModelPolisher  containers use `docker-compose up --detach`.

NOTE: Once database backend is set-up by `docker-compose`, the databases are also available at following ports for `localhost`:
- AnnotateDB: `1013`
- BiGGDB: `1310`

On running these commands, databases will be restored in respective containers. After databases are successfully set up, use the following command (in `ModelPolisher/` directory) to run ModelPolisher:
```
docker-compose run -v <path_directory_containing_models>:/models/ polisher --input=/models/<model_name> --output=/models/output/<output_name> --annotate-with-bigg=true --add-adb-annotations=true --output-combine=true 
```

Note: You must pass *absolute path for host directory*. This command will mount volume `<path_directory_having_models>` to `/models/` in container. Thus, outputs will be produced in directory `<path_directory_having_models>/output`.

User may use `-u <username_or_uid>` with `docker-compose run` in above command to generate output with correct ownership. Preferably use uid (obtained by `id -u <username>`) due to some existing bugs in docker.

To bring down the containers, you can run `docker-compose stop`. This will only stop the containers but won't delete them. Otherwise, you can use `docker-compose down -v` to stop and remove all containers and related volumes.

Note: `docker-compose down -v` will cause all databases to be restored again on running `docker-compose up`, as restored databases exist in containers not in images. 

Using `docker` might cause system space to be filled up, and `docker-compose up` may fail due to insufficient space. Use the following commands if there is need to empty space used by ModelPolisher_docker:
```
docker stop modelpolisher_biggdb modelpolisher_adb modelpolisher_java
docker rm modelpolisher_biggdb modelpolisher_adb modelpolisher_java
docker rmi modelpolisher_java:latest modelpolisher_adb:latest modelpolisher_biggdb:latest postgres:11.4 openjdk:11-slim
docker volume prune
```

#### <a name="non-release">Using a non-release jar with Docker</a>

Building using `gradle devel` builds a container with the local ModelPolisher jar.  
This container can be used analogously to the release version, though either `-f docker-compose.devel.yml` needs to be 
passed to each invocation of `docker-compose` or the `COMPOSE_FILE` environment variable  needs to be set so it points 
to `docker-compose.devel.yml`, e.g. using `export COMPOSE_FILE=docker-compose.devel.yml` for sh or bash. 

## <a name="using-jar"></a>Using ModelPolisher jar
For polishing models, you essentially need to run ModelPolisher using `jar` produced from [build instructions](#build-instructions).

User needs to host the [BiGG](https://github.com/SBRG/bigg_models) Database & [AnnotateDB](https://github.com/matthiaskoenig/annotatedb) on `PostgreSQL` on your local system.

Now, you can run the following command in general:
```
java -jar "<path>/ModelPolisher/target/ModelPolisher-2.1.jar" \
  --input=<input> \
  --output=<output> \
  --output-combine=true \
  --annotate-with-bigg=true \
  --bigg-host=<host> \
  --bigg-port=<port> \
  --bigg-dbname=<postgres_dbname> \
  --bigg-user=<postgres_username> \
  --bigg-passwd=<user_password> \
  --add-adb-annotations=true \
  --adb-host=<host> \
  --adb-port=<port> \
  --adb-dbname=<postgres_dbname> \
  --adb-user=<postgres_username> \
  --adb-passwd=<user_password>
```

We understand problems in setting-up database backend and that a developer would need to build ModelPolisher multiple times and making required changes in `java` Dockerfile will be a tedious task.
We recommend the following practice for developers:
1. Set up required databases by running `docker-compose up`.
2. After making required changes in codebase build `jar` by `gradle lightJar`.
3. Run the newly build jar using:
```
java -jar ./target/ModelPolisher-2.1.jar --input=<input> --output=<output> --output-combine=true --annotate-with-bigg=true --bigg-host=0.0.0.0 --bigg-port=1310 --add-adb-annotations=true --adb-host=0.0.0.0 --adb-port=1013
```
Note: All above commands must be run in `<path>/ModelPolisher/` directory and you must have installed Java `version >= 8` and Gradle `version >= 5.0`.
# <a name="licenses"></a>Licenses

ModelPolisher is distributed under the MIT License (see LICENSE).
An Overview of all dependencies is provided in [THIRD-PARTY.txt](https://github.com/draeger-lab/ModelPolisher/blob/master/THIRD-PARTY.txt), their respective licenses can be found in the licenses folder.
