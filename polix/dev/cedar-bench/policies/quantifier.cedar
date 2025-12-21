// Cedar quantifier-equivalent policies using set operations
// Note: Cedar uses containsAll/containsAny for set operations rather than
// iteration-based quantifiers like OPA's every/some

// Forall-equivalent: All required flags must be present (containsAll)
// This checks if principal.active-flags contains all of {"flag1", "flag2", "flag3", "flag4", "flag5"}
permit(
    principal,
    action == Action::"access",
    resource
) when {
    principal["active-flags"].containsAll([
        "flag1", "flag2", "flag3", "flag4", "flag5"
    ])
};
