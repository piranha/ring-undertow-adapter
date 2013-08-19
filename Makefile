NAME := $(shell awk '/defproject/ { print $$2 }' project.clj)
VERSION := $(shell awk '/defproject/ { gsub("\"", "", $$3); print $$3 }' project.clj)
JAR := target/$(NAME)-$(VERSION).jar

help:
	@echo "Use 'lein <command>', additional commands:"
	@echo "  make pub - publish to clojars"

pub: pom.xml $(JAR)
	scp $^ clojars@clojars.org:

pom.xml: project.clj
	lein pom

$(JAR): $(shell find src -name '*.clj')
	lein jar


