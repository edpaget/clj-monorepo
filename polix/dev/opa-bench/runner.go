package main

import (
	"context"
	"embed"
	"fmt"
	"math"
	"runtime"
	"sort"
	"time"

	"github.com/open-policy-agent/opa/v1/rego"
)

//go:embed policies/*.rego
var policies embed.FS

type BenchmarkResult struct {
	Name    string                 `json:"name"`
	Results map[string]interface{} `json:"results"`
}

type PreparedPolicy struct {
	Name  string
	Query rego.PreparedEvalQuery
}

func preparePolicy(name string, filename string) (PreparedPolicy, error) {
	ctx := context.Background()

	policyBytes, err := policies.ReadFile("policies/" + filename)
	if err != nil {
		return PreparedPolicy{}, fmt.Errorf("reading %s: %w", filename, err)
	}

	query, err := rego.New(
		rego.Query("data.policy."+name+".allow"),
		rego.Module(filename, string(policyBytes)),
	).PrepareForEval(ctx)
	if err != nil {
		return PreparedPolicy{}, fmt.Errorf("preparing %s: %w", name, err)
	}

	return PreparedPolicy{Name: name, Query: query}, nil
}

func preparePolicies() ([]PreparedPolicy, error) {
	policyDefs := []struct {
		name     string
		filename string
	}{
		{"simple", "simple.rego"},
		{"medium", "medium.rego"},
		{"complex", "complex.rego"},
	}

	var prepared []PreparedPolicy
	for _, def := range policyDefs {
		p, err := preparePolicy(def.name, def.filename)
		if err != nil {
			return nil, err
		}
		prepared = append(prepared, p)
	}
	return prepared, nil
}

func prepareQuantifierPolicy(name string, ruleName string) (PreparedPolicy, error) {
	ctx := context.Background()

	policyBytes, err := policies.ReadFile("policies/quantifier.rego")
	if err != nil {
		return PreparedPolicy{}, fmt.Errorf("reading quantifier.rego: %w", err)
	}

	query, err := rego.New(
		rego.Query("data.policy.quantifier."+ruleName),
		rego.Module("quantifier.rego", string(policyBytes)),
	).PrepareForEval(ctx)
	if err != nil {
		return PreparedPolicy{}, fmt.Errorf("preparing %s: %w", name, err)
	}

	return PreparedPolicy{Name: name, Query: query}, nil
}

func prepareQuantifierPolicies() ([]PreparedPolicy, error) {
	quantifierDefs := []struct {
		name     string
		ruleName string
	}{
		{"forall_simple", "forall_simple"},
		{"forall_nested", "forall_nested"},
		{"exists_simple", "exists_simple"},
		{"nested_forall_exists", "nested_forall_exists"},
	}

	var prepared []PreparedPolicy
	for _, def := range quantifierDefs {
		p, err := prepareQuantifierPolicy(def.name, def.ruleName)
		if err != nil {
			return nil, err
		}
		prepared = append(prepared, p)
	}
	return prepared, nil
}

func prepareCountFilterPolicy(name string, ruleName string) (PreparedPolicy, error) {
	ctx := context.Background()

	policyBytes, err := policies.ReadFile("policies/count_filter.rego")
	if err != nil {
		return PreparedPolicy{}, fmt.Errorf("reading count_filter.rego: %w", err)
	}

	query, err := rego.New(
		rego.Query("data.policy.count_filter."+ruleName),
		rego.Module("count_filter.rego", string(policyBytes)),
	).PrepareForEval(ctx)
	if err != nil {
		return PreparedPolicy{}, fmt.Errorf("preparing %s: %w", name, err)
	}

	return PreparedPolicy{Name: name, Query: query}, nil
}

func prepareCountFilterPolicies() ([]PreparedPolicy, error) {
	countFilterDefs := []struct {
		name     string
		ruleName string
	}{
		{"count_simple", "count_simple"},
		{"count_medium", "count_medium"},
		{"count_large", "count_large"},
		{"count_nested", "count_nested"},
		{"count_with_comparison", "count_with_comparison"},
		{"forall_filtered", "forall_filtered"},
		{"exists_filtered", "exists_filtered"},
		{"count_filtered", "count_filtered"},
		{"count_filtered_complex", "count_filtered_complex"},
		{"nested_filtered", "nested_filtered"},
	}

	var prepared []PreparedPolicy
	for _, def := range countFilterDefs {
		p, err := prepareCountFilterPolicy(def.name, def.ruleName)
		if err != nil {
			return nil, err
		}
		prepared = append(prepared, p)
	}
	return prepared, nil
}

func mean(samples []float64) float64 {
	sum := 0.0
	for _, s := range samples {
		sum += s
	}
	return sum / float64(len(samples))
}

func stdDev(samples []float64, mean float64) float64 {
	sumSq := 0.0
	for _, s := range samples {
		diff := s - mean
		sumSq += diff * diff
	}
	return math.Sqrt(sumSq / float64(len(samples)))
}

func percentile(samples []float64, p float64) float64 {
	sorted := make([]float64, len(samples))
	copy(sorted, samples)
	sort.Float64s(sorted)
	idx := int(float64(len(sorted)-1) * p)
	return sorted[idx]
}

func runBenchmark(name string, query rego.PreparedEvalQuery, input map[string]interface{}) BenchmarkResult {
	ctx := context.Background()
	const warmupIterations = 100
	const sampleIterations = 1000

	// Warmup
	for i := 0; i < warmupIterations; i++ {
		query.Eval(ctx, rego.EvalInput(input))
	}

	// Force GC before measurement
	runtime.GC()

	// Collect samples
	samples := make([]float64, sampleIterations)
	for i := 0; i < sampleIterations; i++ {
		start := time.Now()
		query.Eval(ctx, rego.EvalInput(input))
		samples[i] = float64(time.Since(start).Nanoseconds())
	}

	m := mean(samples)
	sd := stdDev(samples, m)

	return BenchmarkResult{
		Name: name,
		Results: map[string]interface{}{
			"mean-ns":  int64(m),
			"std-dev":  int64(sd),
			"lower-q":  int64(percentile(samples, 0.25)),
			"upper-q":  int64(percentile(samples, 0.75)),
			"samples":  sampleIterations,
			"gc-count": nil,
		},
	}
}

type benchDef struct {
	name   string
	policy string
	doc    map[string]interface{}
}

func runAllBenchmarks() ([]BenchmarkResult, error) {
	fmt.Println("Preparing policies...")
	prepared, err := preparePolicies()
	if err != nil {
		return nil, err
	}

	policyMap := make(map[string]rego.PreparedEvalQuery)
	for _, p := range prepared {
		policyMap[p.Name] = p.Query
	}

	benchmarks := []benchDef{
		{"opa/simple-satisfied", "simple", docSimpleSatisfied},
		{"opa/simple-contradicted", "simple", docSimpleContradicted},
		{"opa/medium-satisfied", "medium", docMediumSatisfied},
		{"opa/medium-partial", "medium", docMediumPartial},
		{"opa/complex-satisfied", "complex", docComplexSatisfied},
		{"opa/complex-partial", "complex", docComplexPartial},
	}

	fmt.Println("Running benchmarks...")
	var results []BenchmarkResult
	for _, b := range benchmarks {
		fmt.Printf("  %s...", b.name)
		result := runBenchmark(b.name, policyMap[b.policy], b.doc)
		results = append(results, result)
		fmt.Printf(" %d ns\n", result.Results["mean-ns"])
	}

	// Run quantifier benchmarks
	fmt.Println("Preparing quantifier policies...")
	quantifierPolicies, err := prepareQuantifierPolicies()
	if err != nil {
		return nil, err
	}

	quantifierMap := make(map[string]rego.PreparedEvalQuery)
	for _, p := range quantifierPolicies {
		quantifierMap[p.Name] = p.Query
	}

	quantifierBenchmarks := []benchDef{
		{"opa/quantifier/forall-small-satisfied", "forall_simple", docUsers5AllActive},
		{"opa/quantifier/forall-small-contradicted", "forall_simple", docUsers5OneInactive},
		{"opa/quantifier/forall-medium-satisfied", "forall_nested", docUsers20AllVerified},
		{"opa/quantifier/forall-large-satisfied", "forall_simple", docUsers100AllActive},
		{"opa/quantifier/exists-small-satisfied", "exists_simple", docUsers5FirstAdmin},
		{"opa/quantifier/exists-small-contradicted", "exists_simple", docUsers5NoAdmin},
		{"opa/quantifier/exists-large-early-exit", "exists_simple", docUsers100FirstAdmin},
		{"opa/quantifier/exists-large-late-exit", "exists_simple", docUsers100LastAdmin},
		{"opa/quantifier/nested-satisfied", "nested_forall_exists", docTeamsAllHaveLead},
		{"opa/quantifier/nested-contradicted", "nested_forall_exists", docTeamsOneMissingLead},
	}

	fmt.Println("Running quantifier benchmarks...")
	for _, b := range quantifierBenchmarks {
		fmt.Printf("  %s...", b.name)
		result := runBenchmark(b.name, quantifierMap[b.policy], b.doc)
		results = append(results, result)
		fmt.Printf(" %d ns\n", result.Results["mean-ns"])
	}

	// Run count and filtered benchmarks
	fmt.Println("Preparing count/filter policies...")
	countFilterPolicies, err := prepareCountFilterPolicies()
	if err != nil {
		return nil, err
	}

	countFilterMap := make(map[string]rego.PreparedEvalQuery)
	for _, p := range countFilterPolicies {
		countFilterMap[p.Name] = p.Query
	}

	countBenchmarks := []benchDef{
		{"opa/count/simple-5-satisfied", "count_simple", docUsers5AllActive},
		{"opa/count/simple-5-contradicted", "count_simple", map[string]interface{}{"users": makeUsers(3, true)}},
		{"opa/count/medium-20-satisfied", "count_medium", docUsers20AllVerified},
		{"opa/count/large-100-satisfied", "count_large", docUsers100AllActive},
		{"opa/count/nested-path", "count_nested", docOrgWithMembers},
		{"opa/count/with-comparison", "count_with_comparison", map[string]interface{}{
			"users":  makeUsers(5, true),
			"active": true,
		}},
	}

	fmt.Println("Running count benchmarks...")
	for _, b := range countBenchmarks {
		fmt.Printf("  %s...", b.name)
		result := runBenchmark(b.name, countFilterMap[b.policy], b.doc)
		results = append(results, result)
		fmt.Printf(" %d ns\n", result.Results["mean-ns"])
	}

	filteredBenchmarks := []benchDef{
		// Forall with filter
		{"opa/filtered/forall-small-satisfied", "forall_filtered", docUsers5AllActiveVerified},
		{"opa/filtered/forall-small-mixed", "forall_filtered", docUsers5MixedActive},
		{"opa/filtered/forall-medium", "forall_filtered", docUsers20HalfActive},
		{"opa/filtered/forall-large", "forall_filtered", docUsers100MostlyActive},
		// Exists with filter
		{"opa/filtered/exists-small-satisfied", "exists_filtered", docUsers5ActiveWithAdmin},
		{"opa/filtered/exists-small-contradicted", "exists_filtered", docUsers5ActiveNoAdmin},
		{"opa/filtered/exists-large-early", "exists_filtered", docUsers100ActiveFirstAdmin},
		{"opa/filtered/exists-large-late", "exists_filtered", docUsers100ActiveLastAdmin},
		// Count with filter
		{"opa/filtered/count-simple", "count_filtered", docUsers5MixedActive},
		{"opa/filtered/count-medium", "count_filtered", docUsers20HalfActive},
		{"opa/filtered/count-large", "count_filtered", docUsers100MostlyActive},
		{"opa/filtered/count-complex", "count_filtered_complex", docUsers100MostlyActive},
		// Nested with filter
		{"opa/filtered/nested-satisfied", "nested_filtered", docTeams5ActiveWithLeads},
		{"opa/filtered/nested-contradicted", "nested_filtered", docTeams5ActiveMissingLead},
	}

	fmt.Println("Running filtered binding benchmarks...")
	for _, b := range filteredBenchmarks {
		fmt.Printf("  %s...", b.name)
		result := runBenchmark(b.name, countFilterMap[b.policy], b.doc)
		results = append(results, result)
		fmt.Printf(" %d ns\n", result.Results["mean-ns"])
	}

	return results, nil
}
