package main

import (
	"context"
	"testing"

	"github.com/open-policy-agent/opa/v1/rego"
)

var (
	simpleQuery  rego.PreparedEvalQuery
	mediumQuery  rego.PreparedEvalQuery
	complexQuery rego.PreparedEvalQuery

	// Quantifier queries
	forallSimpleQuery      rego.PreparedEvalQuery
	forallNestedQuery      rego.PreparedEvalQuery
	existsSimpleQuery      rego.PreparedEvalQuery
	nestedForallExistsQuery rego.PreparedEvalQuery
)

func init() {
	policies, _ := preparePolicies()
	for _, p := range policies {
		switch p.Name {
		case "simple":
			simpleQuery = p.Query
		case "medium":
			mediumQuery = p.Query
		case "complex":
			complexQuery = p.Query
		}
	}

	// Initialize quantifier queries
	quantifierPolicies, _ := prepareQuantifierPolicies()
	for _, p := range quantifierPolicies {
		switch p.Name {
		case "forall_simple":
			forallSimpleQuery = p.Query
		case "forall_nested":
			forallNestedQuery = p.Query
		case "exists_simple":
			existsSimpleQuery = p.Query
		case "nested_forall_exists":
			nestedForallExistsQuery = p.Query
		}
	}
}

func BenchmarkSimpleSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		simpleQuery.Eval(ctx, rego.EvalInput(docSimpleSatisfied))
	}
}

func BenchmarkSimpleContradicted(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		simpleQuery.Eval(ctx, rego.EvalInput(docSimpleContradicted))
	}
}

func BenchmarkMediumSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		mediumQuery.Eval(ctx, rego.EvalInput(docMediumSatisfied))
	}
}

func BenchmarkMediumPartial(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		mediumQuery.Eval(ctx, rego.EvalInput(docMediumPartial))
	}
}

func BenchmarkComplexSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		complexQuery.Eval(ctx, rego.EvalInput(docComplexSatisfied))
	}
}

func BenchmarkComplexPartial(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		complexQuery.Eval(ctx, rego.EvalInput(docComplexPartial))
	}
}

// Quantifier benchmarks

func BenchmarkForallSmallSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		forallSimpleQuery.Eval(ctx, rego.EvalInput(docUsers5AllActive))
	}
}

func BenchmarkForallSmallContradicted(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		forallSimpleQuery.Eval(ctx, rego.EvalInput(docUsers5OneInactive))
	}
}

func BenchmarkForallMediumSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		forallNestedQuery.Eval(ctx, rego.EvalInput(docUsers20AllVerified))
	}
}

func BenchmarkForallLargeSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		forallSimpleQuery.Eval(ctx, rego.EvalInput(docUsers100AllActive))
	}
}

func BenchmarkExistsSmallSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		existsSimpleQuery.Eval(ctx, rego.EvalInput(docUsers5FirstAdmin))
	}
}

func BenchmarkExistsSmallContradicted(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		existsSimpleQuery.Eval(ctx, rego.EvalInput(docUsers5NoAdmin))
	}
}

func BenchmarkExistsLargeEarlyExit(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		existsSimpleQuery.Eval(ctx, rego.EvalInput(docUsers100FirstAdmin))
	}
}

func BenchmarkExistsLargeLateExit(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		existsSimpleQuery.Eval(ctx, rego.EvalInput(docUsers100LastAdmin))
	}
}

func BenchmarkNestedSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		nestedForallExistsQuery.Eval(ctx, rego.EvalInput(docTeamsAllHaveLead))
	}
}

func BenchmarkNestedContradicted(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		nestedForallExistsQuery.Eval(ctx, rego.EvalInput(docTeamsOneMissingLead))
	}
}
