// Cedar count benchmarks using set size operations
// Note: Cedar doesn't support iteration-based filtering like OPA/Polix
// These benchmarks use set operations as the closest equivalent

// Count simple - user-ids set size >= 5
permit(
    principal,
    action == Action::"access",
    resource
) when {
    principal["user-ids"].containsAll(["u1", "u2", "u3", "u4", "u5"])
};
