package policy.count_filter

# Count simple - count users >= 5
count_simple if {
	count(input.users) >= 5
}

# Count medium - count users >= 20
count_medium if {
	count(input.users) >= 20
}

# Count large - count users >= 100
count_large if {
	count(input.users) >= 100
}

# Count nested path - count org.members >= 5
count_nested if {
	count(input.org.members) >= 5
}

# Count with comparison - count users >= 5 AND active
count_with_comparison if {
	count(input.users) >= 5
	input.active == true
}

# Forall filtered - all active users must have verified profile
forall_filtered if {
	active_users := [u | u := input.users[_]; u.active == true]
	every u in active_users {
		u.profile.verified == true
	}
}

# Exists filtered - at least one active user is admin
exists_filtered if {
	some u in input.users
	u.active == true
	u.role == "admin"
}

# Count filtered - count active users >= 3
count_filtered if {
	active_count := count([u | u := input.users[_]; u.active == true])
	active_count >= 3
}

# Count filtered complex - count active users with score > 80 >= 2
count_filtered_complex if {
	filtered_count := count([u | u := input.users[_]; u.active == true; u.score > 80])
	filtered_count >= 2
}

# Nested filtered - all active teams have at least one high-level lead
nested_filtered if {
	active_teams := [t | t := input.teams[_]; t.active == true]
	every team in active_teams {
		high_level_leads := [m | m := team.members[_]; m.level > 5]
		some m in high_level_leads
		m.role == "lead"
	}
}
