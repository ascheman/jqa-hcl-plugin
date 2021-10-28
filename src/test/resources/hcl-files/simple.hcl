# Test data representing different kinds of HCL expressible language constructs
# Unfortunately the underlying hcl4j library has some errors, i.e.,
# - https://github.com/bertramdev/hcl4j/issues/16[bug 1]: block identifiers parsing error
# - ...[bug 2]: expressions parsing error

# Assign a literal to an attribute
dummy = "some string"

# Assign a variable to an attribute
other = dummy

# Assign (the value) of an literal expression to a variable
# CAUTION the operator and the second parameter are silently dropped -> [bug 2]
five = 3 + 2

# Assign (the value) of an expression with a variable
# CAUTION the operator and the second parameter are silently dropped -> [bug 2]
seven = 2 + five

# one line block
block "oneliner" {}

# Block with two names given by string literals
service "dummy" "this" {
  port = 8080
  networks = ["nw1", "nw2"]
}

# Block with some identifier as additional name
# CAUTION the block is identified by the second identifier, the string literal "2nd" is silently dropped -> [bug 1]
service "2nd" "secondservice" {
  host = "localhost"

  sub {
    subsub "subSubBlock" {
      dummy = "subSubBlockDummy"
    }
  }
}