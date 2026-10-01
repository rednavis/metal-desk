# The built-in "terraform" ruleset needs no download, so the linter runs offline and without credentials.
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}
