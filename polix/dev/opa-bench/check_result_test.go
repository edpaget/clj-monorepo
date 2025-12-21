package main

import (
	"context"
	"fmt"

	"github.com/open-policy-agent/opa/v1/rego"
)

func main() {
	ctx := context.Background()
	policyBytes, _ := policies.ReadFile("policies/simple.rego")
	
	query, _ := rego.New(
		rego.Query("data.policy.simple.allow"),
		rego.Module("simple.rego", string(policyBytes)),
	).PrepareForEval(ctx)
	
	result, _ := query.Eval(ctx, rego.EvalInput(docSimpleSatisfied))
	
	fmt.Printf("Result type: %T\n", result)
	fmt.Printf("Result: %+v\n", result)
	fmt.Printf("Result len: %d\n", len(result))
	if len(result) > 0 {
		fmt.Printf("First result: %+v\n", result[0])
		fmt.Printf("Expressions: %+v\n", result[0].Expressions)
		fmt.Printf("Bindings: %+v\n", result[0].Bindings)
	}
}
