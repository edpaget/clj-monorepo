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

	return results, nil
}
