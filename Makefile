SHELL := /bin/bash
.DEFAULT_GOAL := help
MAKEFLAGS += --no-print-directory

# You can set these variables from the command line, and also from the environment for the first two.
BUILDDIR      = build
MAKE          = make
VERSION       = $(shell cat VERSION)

app_root = $(PROJ_DIR)
app_root ?= .
pkg_src =  $(app_root)/src/main
tests_src = $(app_root)/main/test
package_name =

################################################################################
# Developing \
DEVELOP: ## ############################################################

.PHONY: init
init:  ## init

################################################################################
# Building, Deploying \
BUILDING:  ## ############################################################
.PHONY: all
all: build test  ## build and test

.PHONY: build
build:  ## mvn package
	mvn clean install -DskipTests  # compiles the project, runs any tests, and then installs the resulting artifacts into your local Maven repository.

################################################################################
# Testing \
TESTING:  ## ############################################################


################################################################################
# Clean \
CLEAN:  ## ############################################################
.PHONY: clean-java
clean-java:  ## clean-java
	pushd $(package_name) && mvn clean

################################################################################
# Misc \
MISC:  ## ############################################################
define PRINT_HELP_PYSCRIPT
import re, sys

for line in sys.stdin:
	match = re.match(r'^([a-zA-Z0-9_-]+):.*?## (.*)$$', line)
	if match:
		target, help = match.groups()
		print("\033[36m%-20s\033[0m %s" % (target, help))
endef
export PRINT_HELP_PYSCRIPT

.PHONY: help
help:
	@python -c "$$PRINT_HELP_PYSCRIPT" < $(MAKEFILE_LIST)
