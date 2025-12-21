package policy.quantifier

# Forall - all users must be active
forall_simple if {
	every u in input.users {
		u.active == true
	}
}

# Forall with nested path - all users must have verified profile
forall_nested if {
	every u in input.users {
		u.profile.verified == true
	}
}

# Exists - at least one admin
exists_simple if {
	some u in input.users
	u.role == "admin"
}

# Nested - every team has at least one lead
nested_forall_exists if {
	every team in input.teams {
		some member in team.members
		member.role == "lead"
	}
}
