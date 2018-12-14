#!/bin/sh
case "$(uname)" in
	CYGWIN*|MINGW*) printf ";";;
	*) printf ":";;
esac
