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
	forallSimpleQuery       rego.PreparedEvalQuery
	forallNestedQuery       rego.PreparedEvalQuery
	existsSimpleQuery       rego.PreparedEvalQuery
	nestedForallExistsQuery rego.PreparedEvalQuery

	// Count and filter queries
	countSimpleQuery         rego.PreparedEvalQuery
	countMediumQuery         rego.PreparedEvalQuery
	countLargeQuery          rego.PreparedEvalQuery
	countNestedQuery         rego.PreparedEvalQuery
	countWithComparisonQuery rego.PreparedEvalQuery
	forallFilteredQuery      rego.PreparedEvalQuery
	existsFilteredQuery      rego.PreparedEvalQuery
	countFilteredQuery       rego.PreparedEvalQuery
	countFilteredComplexQuery rego.PreparedEvalQuery
	nestedFilteredQuery      rego.PreparedEvalQuery
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

	// Initialize count and filter queries
	countFilterPolicies, _ := prepareCountFilterPolicies()
	for _, p := range countFilterPolicies {
		switch p.Name {
		case "count_simple":
			countSimpleQuery = p.Query
		case "count_medium":
			countMediumQuery = p.Query
		case "count_large":
			countLargeQuery = p.Query
		case "count_nested":
			countNestedQuery = p.Query
		case "count_with_comparison":
			countWithComparisonQuery = p.Query
		case "forall_filtered":
			forallFilteredQuery = p.Query
		case "exists_filtered":
			existsFilteredQuery = p.Query
		case "count_filtered":
			countFilteredQuery = p.Query
		case "count_filtered_complex":
			countFilteredComplexQuery = p.Query
		case "nested_filtered":
			nestedFilteredQuery = p.Query
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

// Count benchmarks

func BenchmarkCountSimpleSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countSimpleQuery.Eval(ctx, rego.EvalInput(docUsers5AllActive))
	}
}

func BenchmarkCountSimpleContradicted(b *testing.B) {
	ctx := context.Background()
	doc := map[string]interface{}{"users": makeUsers(3, true)}
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countSimpleQuery.Eval(ctx, rego.EvalInput(doc))
	}
}

func BenchmarkCountMediumSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countMediumQuery.Eval(ctx, rego.EvalInput(docUsers20AllVerified))
	}
}

func BenchmarkCountLargeSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countLargeQuery.Eval(ctx, rego.EvalInput(docUsers100AllActive))
	}
}

func BenchmarkCountNestedPath(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countNestedQuery.Eval(ctx, rego.EvalInput(docOrgWithMembers))
	}
}

func BenchmarkCountWithComparison(b *testing.B) {
	ctx := context.Background()
	doc := map[string]interface{}{
		"users":  makeUsers(5, true),
		"active": true,
	}
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countWithComparisonQuery.Eval(ctx, rego.EvalInput(doc))
	}
}

// Filtered binding benchmarks

func BenchmarkFilteredForallSmallSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		forallFilteredQuery.Eval(ctx, rego.EvalInput(docUsers5AllActiveVerified))
	}
}

func BenchmarkFilteredForallSmallMixed(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		forallFilteredQuery.Eval(ctx, rego.EvalInput(docUsers5MixedActive))
	}
}

func BenchmarkFilteredForallMedium(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		forallFilteredQuery.Eval(ctx, rego.EvalInput(docUsers20HalfActive))
	}
}

func BenchmarkFilteredForallLarge(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		forallFilteredQuery.Eval(ctx, rego.EvalInput(docUsers100MostlyActive))
	}
}

func BenchmarkFilteredExistsSmallSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		existsFilteredQuery.Eval(ctx, rego.EvalInput(docUsers5ActiveWithAdmin))
	}
}

func BenchmarkFilteredExistsSmallContradicted(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		existsFilteredQuery.Eval(ctx, rego.EvalInput(docUsers5ActiveNoAdmin))
	}
}

func BenchmarkFilteredExistsLargeEarly(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		existsFilteredQuery.Eval(ctx, rego.EvalInput(docUsers100ActiveFirstAdmin))
	}
}

func BenchmarkFilteredExistsLargeLate(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		existsFilteredQuery.Eval(ctx, rego.EvalInput(docUsers100ActiveLastAdmin))
	}
}

func BenchmarkFilteredCountSimple(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countFilteredQuery.Eval(ctx, rego.EvalInput(docUsers5MixedActive))
	}
}

func BenchmarkFilteredCountMedium(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countFilteredQuery.Eval(ctx, rego.EvalInput(docUsers20HalfActive))
	}
}

func BenchmarkFilteredCountLarge(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countFilteredQuery.Eval(ctx, rego.EvalInput(docUsers100MostlyActive))
	}
}

func BenchmarkFilteredCountComplex(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		countFilteredComplexQuery.Eval(ctx, rego.EvalInput(docUsers100MostlyActive))
	}
}

func BenchmarkFilteredNestedSatisfied(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		nestedFilteredQuery.Eval(ctx, rego.EvalInput(docTeams5ActiveWithLeads))
	}
}

func BenchmarkFilteredNestedContradicted(b *testing.B) {
	ctx := context.Background()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		nestedFilteredQuery.Eval(ctx, rego.EvalInput(docTeams5ActiveMissingLead))
	}
}
