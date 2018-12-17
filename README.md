![Travis CI status](https://travis-ci.com/jyn514/lox.svg?branch=master)

## Dependencies
- Java 9+ (because `Map.of` is too nice to live without)
- llvm
  - `lli` for the REPL
  - `clang` for outputting an executable. Despite what it looks like, `clang` does not compile any code, it just assembles and links the .ll file. This is because the equivalent shell script is both 6 lines and system dependant.

### Optional dependencies
- `libreadline` (for readline in the REPL)

## Differences from upstream:

### Lexer
- allows escape characters
- allows numbers to start with a '.'
- disallows multi-line strings
- logs column as well as line number on error
- logs multiple illegal tokens in a row as a single error
- use 'null' instead of 'nil'
- use c-style function declarations instead of 'fun'
- adds several operators:
    - unaries (PLUS_PLUS, MINUS_MINUS)
    - bitwise functions (PIPE, AMPERSAND, PERCENT, CARET)
    - enhanced assignment (PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL, DOT_EQUAL, CARET_EQUAL, PERCENT_EQUAL, AMPERSAND_EQUAL)

