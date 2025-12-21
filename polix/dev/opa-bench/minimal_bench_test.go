package main

import (
	"context"
	"testing"

	"github.com/open-policy-agent/opa/v1/rego"
)

// Test minimal overhead - just the Eval call with pre-made input
func BenchmarkMinimalOverhead(b *testing.B) {
	ctx := context.Background()
	policyBytes, _ := policies.ReadFile("policies/simple.rego")
	
	query, _ := rego.New(
		rego.Query("data.policy.simple.allow"),
		rego.Module("simple.rego", string(policyBytes)),
	).PrepareForEval(ctx)
	
	// Pre-create the eval option
	evalInput := rego.EvalInput(docSimpleSatisfied)
	
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		query.Eval(ctx, evalInput)
	}
}

// Compare with just a map lookup (baseline)
func BenchmarkMapLookup(b *testing.B) {
	doc := docSimpleSatisfied
	for i := 0; i < b.N; i++ {
		_ = doc["role"] == "admin"
	}
}

// Simulate what polix does - just constraint checking
func BenchmarkConstraintCheck(b *testing.B) {
	doc := docSimpleSatisfied
	for i := 0; i < b.N; i++ {
		role, ok := doc["role"]
		if ok {
			_ = role == "admin"
		}
	}
}
