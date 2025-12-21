package policy.complex

# Superadmin path
allow if {
	input.role == "superadmin"
	input.clearance > 9
}

# Admin path
allow if {
	input.role == "admin"
	input.level > 10
	input.status in {"active"}
	department_allowed
}

department_allowed if {
	input.department == "security"
}

department_allowed if {
	input.department == "engineering"
	input.tenure > 5
}

# Moderator path
allow if {
	input.role == "moderator"
	moderator_karma_check
	input.region in {"us", "eu", "apac"}
	not input["restricted-flag"] in {"flagged", "suspended"}
}

moderator_karma_check if {
	input.karma > 1000
	input.warnings < 3
}

moderator_karma_check if {
	input.reputation > 500
	input.verified == true
}

# User path
allow if {
	input.role == "user"
	input["account-age"] > 365
	input["trust-score"] >= 90
	input.subscription in {"premium", "enterprise"}
	not input.trial == true
}
