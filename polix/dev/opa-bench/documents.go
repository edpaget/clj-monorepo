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
