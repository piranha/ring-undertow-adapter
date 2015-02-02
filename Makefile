help:
	@echo "Use 'lein <command>', additional commands:"
	@echo "  make deploy - publish to clojars"

deploy:
	lein deploy clojars
