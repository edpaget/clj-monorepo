package policy.medium

allow if {
	input.role == "admin"
	input.level > 5
	input.status in {"active", "pending"}
	input.age < 65
	input.score >= 80
}
