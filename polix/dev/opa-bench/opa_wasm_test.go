// Check if OPA has a faster evaluation path
package main

import (
	"context"
	"fmt"
	"github.com/open-policy-agent/opa/v1/rego"
)

func main() {
	// Check what PrepareForEval actually does
	ctx := context.Background()
	
	policyBytes, _ := policies.ReadFile("policies/simple.rego")
	
	// Try with different optimization levels
	query, _ := rego.New(
		rego.Query("data.policy.simple.allow"),
		rego.Module("simple.rego", string(policyBytes)),
	).PrepareForEval(ctx)
	
	// Check the query type
	fmt.Printf("Query type: %T\n", query)
	
	// Run a single eval to see what's happening
	result, _ := query.Eval(ctx, rego.EvalInput(docSimpleSatisfied))
	fmt.Printf("Result: %v\n", result)
}
