use cedar_policy::{
    Authorizer, Context, Entities, EntityId, EntityTypeName, EntityUid, Policy, PolicyId,
    PolicySet, Request, Schema,
};
use serde::{Deserialize, Serialize};
use std::str::FromStr;

pub const SCHEMA_SRC: &str = include_str!("../schema.cedarschema");
pub const SIMPLE_POLICY: &str = include_str!("../policies/simple.cedar");
pub const MEDIUM_POLICY: &str = include_str!("../policies/medium.cedar");
pub const COMPLEX_POLICY: &str = include_str!("../policies/complex.cedar");

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BenchmarkResult {
    pub name: String,
    pub results: BenchmarkStats,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct BenchmarkStats {
    #[serde(rename = "mean-ns")]
    pub mean_ns: i64,
    #[serde(rename = "std-dev")]
    pub std_dev: i64,
    #[serde(rename = "lower-q")]
    pub lower_q: i64,
    #[serde(rename = "upper-q")]
    pub upper_q: i64,
    pub samples: i64,
    #[serde(rename = "gc-count")]
    pub gc_count: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ResultsOutput {
    pub timestamp: String,
    pub engine: String,
    pub benchmarks: Vec<BenchmarkResult>,
}

pub struct PreparedBenchmark {
    pub name: String,
    pub authorizer: Authorizer,
    pub policy_set: PolicySet,
    pub entities: Entities,
    pub request: Request,
}

fn user_type() -> EntityTypeName {
    EntityTypeName::from_str("User").unwrap()
}

fn resource_type() -> EntityTypeName {
    EntityTypeName::from_str("Resource").unwrap()
}

fn action_type() -> EntityTypeName {
    EntityTypeName::from_str("Action").unwrap()
}

fn user_uid(id: &str) -> EntityUid {
    EntityUid::from_type_name_and_id(user_type(), EntityId::from_str(id).unwrap())
}

fn resource_uid(id: &str) -> EntityUid {
    EntityUid::from_type_name_and_id(resource_type(), EntityId::from_str(id).unwrap())
}

fn action_uid(id: &str) -> EntityUid {
    EntityUid::from_type_name_and_id(action_type(), EntityId::from_str(id).unwrap())
}

fn parse_schema() -> Schema {
    Schema::from_str(SCHEMA_SRC).expect("Failed to parse schema")
}

fn parse_policy_set(policy_src: &str) -> PolicySet {
    let policy = Policy::parse(None, policy_src).expect("Failed to parse policy");
    let mut policy_set = PolicySet::new();
    policy_set.add(policy).expect("Failed to add policy");
    policy_set
}

fn parse_complex_policy_set() -> PolicySet {
    let mut policy_set = PolicySet::new();
    for (i, policy_text) in COMPLEX_POLICY.split("// ").skip(1).enumerate() {
        let full_text = format!("// {}", policy_text);
        let policy_id = PolicyId::from_str(&format!("complex_{}", i)).unwrap();
        let policy = Policy::parse(Some(policy_id), &full_text)
            .expect("Failed to parse complex policy");
        policy_set.add(policy).expect("Failed to add policy");
    }
    policy_set
}

fn create_entities_json(user_attrs: &str) -> Entities {
    let entities_json = format!(
        r#"[
            {{
                "uid": {{ "type": "User", "id": "test-user" }},
                "attrs": {},
                "parents": []
            }},
            {{
                "uid": {{ "type": "Resource", "id": "test-resource" }},
                "attrs": {{}},
                "parents": []
            }}
        ]"#,
        user_attrs
    );
    Entities::from_json_str(&entities_json, Some(&parse_schema())).expect("Failed to create entities")
}

fn create_request() -> Request {
    Request::new(
        user_uid("test-user"),
        action_uid("access"),
        resource_uid("test-resource"),
        Context::empty(),
        Some(&parse_schema()),
    )
    .expect("Failed to create request")
}

pub fn simple_satisfied() -> PreparedBenchmark {
    let attrs = r#"{ "role": "admin", "level": 10, "status": "active", "age": 30, "score": 95, "department": "engineering", "clearance": 5, "tenure": 6, "karma": 500, "warnings": 0, "reputation": 500, "verified": true, "region": "us", "restricted-flag": "", "account-age": 400, "trust-score": 95, "subscription": "premium", "trial": false }"#;
    PreparedBenchmark {
        name: "cedar/simple-satisfied".to_string(),
        authorizer: Authorizer::new(),
        policy_set: parse_policy_set(SIMPLE_POLICY),
        entities: create_entities_json(attrs),
        request: create_request(),
    }
}

pub fn simple_contradicted() -> PreparedBenchmark {
    let attrs = r#"{ "role": "guest", "level": 2, "status": "banned", "age": 30, "score": 50, "department": "marketing", "clearance": 1, "tenure": 1, "karma": 100, "warnings": 5, "reputation": 100, "verified": false, "region": "other", "restricted-flag": "flagged", "account-age": 30, "trust-score": 50, "subscription": "free", "trial": true }"#;
    PreparedBenchmark {
        name: "cedar/simple-contradicted".to_string(),
        authorizer: Authorizer::new(),
        policy_set: parse_policy_set(SIMPLE_POLICY),
        entities: create_entities_json(attrs),
        request: create_request(),
    }
}

pub fn medium_satisfied() -> PreparedBenchmark {
    let attrs = r#"{ "role": "admin", "level": 10, "status": "active", "age": 30, "score": 95, "department": "engineering", "clearance": 5, "tenure": 6, "karma": 500, "warnings": 0, "reputation": 500, "verified": true, "region": "us", "restricted-flag": "", "account-age": 400, "trust-score": 95, "subscription": "premium", "trial": false }"#;
    PreparedBenchmark {
        name: "cedar/medium-satisfied".to_string(),
        authorizer: Authorizer::new(),
        policy_set: parse_policy_set(MEDIUM_POLICY),
        entities: create_entities_json(attrs),
        request: create_request(),
    }
}

pub fn medium_partial() -> PreparedBenchmark {
    let attrs = r#"{ "role": "admin", "level": 3, "status": "inactive", "age": 70, "score": 50, "department": "marketing", "clearance": 1, "tenure": 1, "karma": 100, "warnings": 5, "reputation": 100, "verified": false, "region": "other", "restricted-flag": "", "account-age": 30, "trust-score": 50, "subscription": "free", "trial": false }"#;
    PreparedBenchmark {
        name: "cedar/medium-partial".to_string(),
        authorizer: Authorizer::new(),
        policy_set: parse_policy_set(MEDIUM_POLICY),
        entities: create_entities_json(attrs),
        request: create_request(),
    }
}

pub fn complex_satisfied() -> PreparedBenchmark {
    let attrs = r#"{ "role": "admin", "level": 15, "status": "active", "age": 30, "score": 95, "department": "security", "clearance": 5, "tenure": 6, "karma": 500, "warnings": 0, "reputation": 500, "verified": true, "region": "us", "restricted-flag": "", "account-age": 400, "trust-score": 95, "subscription": "premium", "trial": false }"#;
    PreparedBenchmark {
        name: "cedar/complex-satisfied".to_string(),
        authorizer: Authorizer::new(),
        policy_set: parse_complex_policy_set(),
        entities: create_entities_json(attrs),
        request: create_request(),
    }
}

pub fn complex_partial() -> PreparedBenchmark {
    let attrs = r#"{ "role": "admin", "level": 15, "status": "inactive", "age": 30, "score": 50, "department": "marketing", "clearance": 1, "tenure": 1, "karma": 100, "warnings": 5, "reputation": 100, "verified": false, "region": "other", "restricted-flag": "", "account-age": 30, "trust-score": 50, "subscription": "free", "trial": false }"#;
    PreparedBenchmark {
        name: "cedar/complex-partial".to_string(),
        authorizer: Authorizer::new(),
        policy_set: parse_complex_policy_set(),
        entities: create_entities_json(attrs),
        request: create_request(),
    }
}

pub fn all_benchmarks() -> Vec<PreparedBenchmark> {
    vec![
        simple_satisfied(),
        simple_contradicted(),
        medium_satisfied(),
        medium_partial(),
        complex_satisfied(),
        complex_partial(),
    ]
}

pub fn run_benchmark(bench: &PreparedBenchmark, warmup: usize, samples: usize) -> BenchmarkResult {
    use std::time::Instant;

    // Warmup
    for _ in 0..warmup {
        let _ = bench
            .authorizer
            .is_authorized(&bench.request, &bench.policy_set, &bench.entities);
    }

    // Collect samples
    let mut times: Vec<f64> = Vec::with_capacity(samples);
    for _ in 0..samples {
        let start = Instant::now();
        let _ = bench
            .authorizer
            .is_authorized(&bench.request, &bench.policy_set, &bench.entities);
        times.push(start.elapsed().as_nanos() as f64);
    }

    // Calculate statistics
    let mean = times.iter().sum::<f64>() / times.len() as f64;
    let variance = times.iter().map(|t| (t - mean).powi(2)).sum::<f64>() / times.len() as f64;
    let std_dev = variance.sqrt();

    times.sort_by(|a, b| a.partial_cmp(b).unwrap());
    let lower_q = times[(times.len() as f64 * 0.25) as usize];
    let upper_q = times[(times.len() as f64 * 0.75) as usize];

    BenchmarkResult {
        name: bench.name.clone(),
        results: BenchmarkStats {
            mean_ns: mean as i64,
            std_dev: std_dev as i64,
            lower_q: lower_q as i64,
            upper_q: upper_q as i64,
            samples: samples as i64,
            gc_count: None,
        },
    }
}
