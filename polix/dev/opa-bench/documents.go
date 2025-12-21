package main

var docSimpleSatisfied = map[string]interface{}{
	"role":   "admin",
	"level":  10,
	"status": "active",
}

var docSimpleContradicted = map[string]interface{}{
	"role":   "guest",
	"level":  2,
	"status": "banned",
}

var docMediumSatisfied = map[string]interface{}{
	"role":       "admin",
	"level":      10,
	"status":     "active",
	"age":        30,
	"score":      95,
	"department": "engineering",
	"suspended":  false,
}

var docMediumPartial = map[string]interface{}{
	"role": "admin",
}

var docComplexSatisfied = map[string]interface{}{
	"role":       "admin",
	"level":      15,
	"status":     "active",
	"department": "security",
	"clearance":  5,
	"karma":      500,
	"warnings":   0,
	"region":     "us",
}

var docComplexPartial = map[string]interface{}{
	"role":  "admin",
	"level": 15,
}

var docEmpty = map[string]interface{}{}

// Quantifier documents

func makeUsers(n int, active bool) []map[string]interface{} {
	users := make([]map[string]interface{}, n)
	for i := 0; i < n; i++ {
		users[i] = map[string]interface{}{
			"active":  active,
			"role":    "user",
			"score":   80,
			"profile": map[string]interface{}{"verified": true},
		}
	}
	return users
}

func makeUsersWithOneInactive(n int) []map[string]interface{} {
	users := makeUsers(n-1, true)
	users = append(users, map[string]interface{}{"active": false})
	return users
}

func makeUsersWithAdmin(n int, adminIndex int) []map[string]interface{} {
	users := make([]map[string]interface{}, n)
	for i := 0; i < n; i++ {
		role := "user"
		if i == adminIndex {
			role = "admin"
		}
		users[i] = map[string]interface{}{"role": role}
	}
	return users
}

func makeTeams(n int, hasLead bool) []map[string]interface{} {
	teams := make([]map[string]interface{}, n)
	for i := 0; i < n; i++ {
		var members []map[string]interface{}
		if hasLead {
			members = []map[string]interface{}{
				{"role": "dev"},
				{"role": "lead"},
				{"role": "dev"},
			}
		} else {
			members = []map[string]interface{}{
				{"role": "dev"},
				{"role": "dev"},
			}
		}
		teams[i] = map[string]interface{}{"members": members}
	}
	return teams
}

func makeTeamsOneMissingLead(n int) []map[string]interface{} {
	teams := makeTeams(n-1, true)
	teams = append(teams, map[string]interface{}{
		"members": []map[string]interface{}{
			{"role": "dev"},
			{"role": "dev"},
		},
	})
	return teams
}

var docUsers5AllActive = map[string]interface{}{
	"users": makeUsers(5, true),
}

var docUsers5OneInactive = map[string]interface{}{
	"users": makeUsersWithOneInactive(5),
}

var docUsers20AllVerified = map[string]interface{}{
	"users": makeUsers(20, true),
}

var docUsers100AllActive = map[string]interface{}{
	"users": makeUsers(100, true),
}

var docUsers5FirstAdmin = map[string]interface{}{
	"users": makeUsersWithAdmin(5, 0),
}

var docUsers5NoAdmin = map[string]interface{}{
	"users": makeUsersWithAdmin(5, -1),
}

var docUsers100FirstAdmin = map[string]interface{}{
	"users": makeUsersWithAdmin(100, 0),
}

var docUsers100LastAdmin = map[string]interface{}{
	"users": makeUsersWithAdmin(100, 99),
}

var docTeamsAllHaveLead = map[string]interface{}{
	"teams": makeTeams(5, true),
}

var docTeamsOneMissingLead = map[string]interface{}{
	"teams": makeTeamsOneMissingLead(5),
}

// Count and filtered binding documents

func makeUsersWithActiveAndProfile(n int, active bool, verified bool, role string, score int) []map[string]interface{} {
	users := make([]map[string]interface{}, n)
	for i := 0; i < n; i++ {
		users[i] = map[string]interface{}{
			"active":  active,
			"role":    role,
			"score":   score,
			"profile": map[string]interface{}{"verified": verified},
		}
	}
	return users
}

func makeActiveTeamsWithLevels(n int, hasHighLevelLead bool) []map[string]interface{} {
	teams := make([]map[string]interface{}, n)
	for i := 0; i < n; i++ {
		var members []map[string]interface{}
		if hasHighLevelLead {
			members = []map[string]interface{}{
				{"role": "dev", "level": 3},
				{"role": "lead", "level": 8},
				{"role": "dev", "level": 4},
			}
		} else {
			members = []map[string]interface{}{
				{"role": "dev", "level": 3},
				{"role": "dev", "level": 4},
			}
		}
		teams[i] = map[string]interface{}{
			"active":  true,
			"members": members,
		}
	}
	return teams
}

func makeActiveTeamsOneMissingLead(n int) []map[string]interface{} {
	teams := makeActiveTeamsWithLevels(n-1, true)
	teams = append(teams, map[string]interface{}{
		"active": true,
		"members": []map[string]interface{}{
			{"role": "dev", "level": 3},
			{"role": "dev", "level": 4},
		},
	})
	return teams
}

// 5 users all active with verified profiles
var docUsers5AllActiveVerified = map[string]interface{}{
	"users": makeUsersWithActiveAndProfile(5, true, true, "user", 90),
}

// 5 users: 3 active verified, 2 inactive
var docUsers5MixedActive = map[string]interface{}{
	"users": append(
		makeUsersWithActiveAndProfile(3, true, true, "user", 90),
		makeUsersWithActiveAndProfile(2, false, false, "user", 50)...,
	),
}

// 20 users: 10 active, 10 inactive
var docUsers20HalfActive = map[string]interface{}{
	"users": append(
		makeUsersWithActiveAndProfile(10, true, true, "user", 90),
		makeUsersWithActiveAndProfile(10, false, false, "user", 50)...,
	),
}

// 100 users: 80 active, 20 inactive
var docUsers100MostlyActive = map[string]interface{}{
	"users": append(
		makeUsersWithActiveAndProfile(80, true, true, "user", 90),
		makeUsersWithActiveAndProfile(20, false, false, "user", 50)...,
	),
}

// 5 active users, first is admin
var docUsers5ActiveWithAdmin = map[string]interface{}{
	"users": append(
		makeUsersWithActiveAndProfile(1, true, true, "admin", 95),
		makeUsersWithActiveAndProfile(4, true, true, "user", 85)...,
	),
}

// 5 active users, none are admin
var docUsers5ActiveNoAdmin = map[string]interface{}{
	"users": makeUsersWithActiveAndProfile(5, true, true, "user", 85),
}

// 100 active users, first is admin
var docUsers100ActiveFirstAdmin = map[string]interface{}{
	"users": append(
		makeUsersWithActiveAndProfile(1, true, true, "admin", 95),
		makeUsersWithActiveAndProfile(99, true, true, "user", 85)...,
	),
}

// 100 active users, last is admin
var docUsers100ActiveLastAdmin = map[string]interface{}{
	"users": append(
		makeUsersWithActiveAndProfile(99, true, true, "user", 85),
		makeUsersWithActiveAndProfile(1, true, true, "admin", 95)...,
	),
}

// Organization with nested members
var docOrgWithMembers = map[string]interface{}{
	"org": map[string]interface{}{
		"name": "Acme",
		"members": []map[string]interface{}{
			{"name": "User1", "level": 5},
			{"name": "User2", "level": 5},
			{"name": "User3", "level": 5},
			{"name": "User4", "level": 5},
			{"name": "User5", "level": 5},
			{"name": "User6", "level": 5},
			{"name": "User7", "level": 5},
			{"name": "User8", "level": 5},
			{"name": "User9", "level": 5},
			{"name": "User10", "level": 5},
		},
	},
	"active": true,
}

// 5 active teams with high-level leads
var docTeams5ActiveWithLeads = map[string]interface{}{
	"teams": makeActiveTeamsWithLevels(5, true),
}

// 5 active teams, one missing high-level lead
var docTeams5ActiveMissingLead = map[string]interface{}{
	"teams": makeActiveTeamsOneMissingLead(5),
}
