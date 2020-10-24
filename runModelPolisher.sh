#!/bin/bash

if [ ! -x "$(command -v docker)" ]; then echo "Docker is not installed, please set up docker"; exit -1; fi
MODELS=$1
if [ -z "${MODELS}" ]; then echo "Path to model folder is either empty or not set. Please set correct path"; exit -2; fi
if [ ! -d "${MODELS}" ]; then echo "Path does not point to directory: ${MODELS}"; exit -3; fi
docker-compose run -u $(id -u):$(id -g) -v ${MODELS}:/models polisher --input=/models --output=/models/out --annotate-with-bigg=true --sbml-validation=true --compression-type=GZIP
