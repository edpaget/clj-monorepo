use cedar_bench::{all_benchmarks, run_benchmark, ResultsOutput};
use chrono::Utc;
use clap::Parser;
use std::fs;

#[derive(Parser, Debug)]
#[command(name = "cedar-bench")]
#[command(about = "Cedar policy benchmark runner")]
struct Args {
    #[arg(short, long, default_value = "cedar-benchmark-results.json")]
    output: String,

    #[arg(long, default_value = "100")]
    warmup: usize,

    #[arg(long, default_value = "1000")]
    samples: usize,
}

fn main() {
    let args = Args::parse();

    println!("Cedar Benchmark Runner");
    println!("======================");
    println!();

    let benchmarks = all_benchmarks();
    let mut results = Vec::new();

    println!("Running benchmarks...");
    for bench in &benchmarks {
        print!("  {}...", bench.name);
        let result = run_benchmark(bench, args.warmup, args.samples);
        println!(" {} ns", result.results.mean_ns);
        results.push(result);
    }

    let output = ResultsOutput {
        timestamp: Utc::now().to_rfc3339_opts(chrono::SecondsFormat::Nanos, true),
        engine: "cedar".to_string(),
        benchmarks: results.clone(),
    };

    let json = serde_json::to_string_pretty(&output).expect("Failed to serialize results");
    fs::write(&args.output, &json).expect("Failed to write output file");

    println!();
    println!("Results written to: {}", args.output);
    println!();
    println!("Benchmark summary:");
    for result in &results {
        println!(
            "  {:35} {:>10} ns (std: {})",
            result.name, result.results.mean_ns, result.results.std_dev
        );
    }
}
