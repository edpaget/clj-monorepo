package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"time"
)

type ResultsOutput struct {
	Timestamp  string            `json:"timestamp"`
	Engine     string            `json:"engine"`
	Benchmarks []BenchmarkResult `json:"benchmarks"`
}

func main() {
	output := flag.String("output", "opa-benchmark-results.json", "Output JSON file")
	flag.Parse()

	fmt.Println("OPA Benchmark Runner")
	fmt.Println("====================")

	results, err := runAllBenchmarks()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}

	data := ResultsOutput{
		Timestamp:  time.Now().UTC().Format(time.RFC3339Nano),
		Engine:     "opa",
		Benchmarks: results,
	}

	jsonData, err := json.MarshalIndent(data, "", "  ")
	if err != nil {
		fmt.Fprintf(os.Stderr, "Error marshaling JSON: %v\n", err)
		os.Exit(1)
	}

	if err := os.WriteFile(*output, jsonData, 0644); err != nil {
		fmt.Fprintf(os.Stderr, "Error writing file: %v\n", err)
		os.Exit(1)
	}

	fmt.Printf("\nResults written to: %s\n", *output)

	fmt.Println("\nBenchmark summary:")
	for _, b := range results {
		fmt.Printf("  %-35s %10d ns (std: %d)\n",
			b.Name,
			b.Results["mean-ns"],
			b.Results["std-dev"])
	}
}
